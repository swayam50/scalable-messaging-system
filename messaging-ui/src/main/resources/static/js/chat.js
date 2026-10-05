/*
 * Chat client.
 * - Gets a short-lived access token from the UI server (/api/v1/session/token, same origin + session cookie).
 * - Talks to chat-service directly: REST for inbox/history/conversations, WebSocket for live messages.
 * - Asks chat-service which node owns this user (/api/v1/connect: consistent-hash ring) and opens the
 *   WebSocket on that node; re-asks on every reconnect, because the owner changes when nodes join/leave.
 * - User names come from the UI server (/api/v1/users), which proxies auth-service.
 * All user content is rendered with textContent (never innerHTML) to prevent XSS.
 */
(() => {
  'use strict';

  const app = document.getElementById('app');
  const ME = app.dataset.meId;
  const CHAT_APIS = app.dataset.chatApis.split(',').map((url) => url.trim()).filter(Boolean);
  const OWNER_CHANGED = 4001;   // close code sent by a node that no longer owns this user

  const el = (id) => document.getElementById(id);
  const state = {
    token: null,
    tokenExpiresAt: 0,
    socket: null,
    node: null,
    reconnectDelay: 1000,
    apiIndex: 0,               // which chat node currently serves our REST calls
    inbox: new Map(),          // conversationId -> inbox entry
    names: new Map(),          // userId -> display name
    current: null,             // { conversationId, peerId, nextBefore }
    pending: new Map(),        // clientMessageId -> <li>
  };

  // ---------- tokens ----------
  async function accessToken() {
    if (state.token && Date.now() < state.tokenExpiresAt - 30_000) {
      return state.token;
    }
    const res = await fetch('/api/v1/session/token', { method: 'POST' });
    if (res.status === 401) {
      window.location.href = '/login';
      throw new Error('Session expired');
    }
    const body = await res.json();
    state.token = body.accessToken;
    state.tokenExpiresAt = Date.now() + body.expiresIn * 1000;
    return state.token;
  }

  /**
   * REST call to chat-service. Every node serves the same API (shared ScyllaDB, same ring),
   * so if the current node is unreachable we fail over to the next one.
   */
  async function chatApi(path, options = {}) {
    const token = await accessToken();
    for (let attempt = 0; attempt < CHAT_APIS.length; attempt++) {
      const base = CHAT_APIS[state.apiIndex];
      let res;
      try {
        res = await fetch(base + path, {
          ...options,
          headers: { ...(options.headers || {}), Authorization: 'Bearer ' + token },
        });
      } catch (networkError) {
        state.apiIndex = (state.apiIndex + 1) % CHAT_APIS.length;   // node down: try the next one
        continue;
      }
      if (!res.ok) throw new Error((await res.json().catch(() => ({}))).detail || res.statusText);
      return res.json();
    }
    throw new Error('No chat node reachable');
  }

  async function displayName(userId) {
    if (userId === ME) return 'You';
    if (!state.names.has(userId)) {
      const res = await fetch('/api/v1/users/' + encodeURIComponent(userId));
      const user = res.ok ? await res.json() : null;
      state.names.set(userId, user ? user.displayName : 'Unknown user');
    }
    return state.names.get(userId);
  }

  // ---------- WebSocket ----------
  async function connect() {
    setStatus('connecting…', '');
    let socket;
    try {
      const owner = await chatApi('/api/v1/connect');   // which node owns me right now?
      const token = await accessToken();
      socket = new WebSocket(owner.wsUrl + '?access_token=' + encodeURIComponent(token));
      state.node = owner.nodeId;
    } catch (e) {
      return scheduleReconnect();
    }
    state.socket = socket;

    socket.onopen = () => {
      setStatus('online · ' + state.node, 'online');
      state.reconnectDelay = 1000;
      // We may have missed messages while disconnected: refresh what's on screen.
      loadInbox();
      if (state.current) openConversation(state.current.conversationId, state.current.peerId);
    };
    socket.onmessage = (event) => handleFrame(JSON.parse(event.data));
    socket.onclose = (event) => {
      if (event.code === OWNER_CHANGED) {
        // Cluster rebalanced: reconnect to the new owner right away
        state.reconnectDelay = 1000;
        setStatus('moving to another node…', '');
        return connect();
      }
      scheduleReconnect();
    };
  }

  function scheduleReconnect() {
    setStatus('offline – reconnecting', 'offline');
    setTimeout(connect, state.reconnectDelay);
    state.reconnectDelay = Math.min(state.reconnectDelay * 2, 30_000);   // exponential backoff
  }

  function handleFrame(frame) {
    if (frame.type === 'ACK') {
      const li = state.pending.get(frame.clientMessageId);
      if (li) {
        li.classList.remove('pending');
        li.dataset.id = frame.message.messageId;
        li.querySelector('.meta').textContent = time(frame.message.sentAt);
        state.pending.delete(frame.clientMessageId);
      }
      touchInbox(frame.message, false);
    } else if (frame.type === 'MESSAGE') {
      const msg = frame.message;
      const isOpen = state.current && state.current.conversationId === msg.conversationId;
      if (isOpen) {
        appendMessage(msg);
        scrollToBottom();
      }
      touchInbox(msg, !isOpen && msg.senderId !== ME);
    } else if (frame.type === 'ERROR') {
      const li = frame.clientMessageId && state.pending.get(frame.clientMessageId);
      if (li) {
        li.classList.replace('pending', 'failed');
        li.querySelector('.meta').textContent = 'not sent: ' + frame.error;
        state.pending.delete(frame.clientMessageId);
      }
    }
  }

  function send(body) {
    if (!state.current || !state.socket || state.socket.readyState !== WebSocket.OPEN) return;
    const clientMessageId = crypto.randomUUID();
    // Optimistic bubble: shown immediately, confirmed by the ACK
    const li = bubble({ senderId: ME, body, sentAt: null }, 'sending…');
    li.classList.add('pending');
    state.pending.set(clientMessageId, li);
    el('message-list').appendChild(li);
    scrollToBottom();
    state.socket.send(JSON.stringify({
      type: 'SEND',
      conversationId: state.current.conversationId,
      clientMessageId,
      body,
    }));
  }

  // ---------- inbox ----------
  async function loadInbox() {
    const entries = await chatApi('/api/v1/conversations');
    state.inbox = new Map(entries.map((e) => [e.conversationId, e]));
    renderInbox();
  }

  function touchInbox(msg, unread) {
    const entry = state.inbox.get(msg.conversationId) || {
      conversationId: msg.conversationId,
      peerId: msg.senderId === ME ? state.current?.peerId : msg.senderId,
    };
    Object.assign(entry, {
      lastMessageId: msg.messageId,
      lastSenderId: msg.senderId,
      preview: msg.body.slice(0, 100),
      lastMessageAt: msg.sentAt,
      unread: unread || entry.unread,
    });
    state.inbox.set(msg.conversationId, entry);
    renderInbox();
  }

  async function renderInbox() {
    // Snowflake ids are strings; BigInt compares them correctly beyond 2^53
    const entries = [...state.inbox.values()].sort((a, b) => {
      if (!a.lastMessageId) return 1;
      if (!b.lastMessageId) return -1;
      return BigInt(b.lastMessageId) > BigInt(a.lastMessageId) ? 1 : -1;
    });
    const list = el('inbox');
    const items = await Promise.all(entries.map(async (entry) => {
      const li = document.createElement('li');
      li.classList.toggle('active', state.current?.conversationId === entry.conversationId);
      li.classList.toggle('unread', !!entry.unread);
      const name = document.createElement('div');
      name.className = 'name';
      const who = document.createElement('span');
      who.textContent = await displayName(entry.peerId);
      const when = document.createElement('span');
      when.className = 'muted small';
      when.textContent = entry.lastMessageAt ? time(entry.lastMessageAt) : '';
      name.append(who, when);
      const preview = document.createElement('div');
      preview.className = 'preview';
      preview.textContent = entry.preview
        ? (entry.lastSenderId === ME ? 'You: ' : '') + entry.preview
        : 'No messages yet';
      li.append(name, preview);
      li.onclick = () => openConversation(entry.conversationId, entry.peerId);
      return li;
    }));
    list.replaceChildren(...items);
  }

  // ---------- conversation ----------
  async function openConversation(conversationId, peerId) {
    state.current = { conversationId, peerId, nextBefore: null };
    const entry = state.inbox.get(conversationId);
    if (entry) entry.unread = false;

    el('empty-state').classList.add('hidden');
    ['conversation-header', 'messages', 'composer'].forEach((id) => el(id).classList.remove('hidden'));
    el('peer-name').textContent = await displayName(peerId);
    el('message-list').replaceChildren();
    state.pending.clear();

    await loadOlder();
    scrollToBottom();
    renderInbox();
    el('message-input').focus();
  }

  async function loadOlder() {
    const { conversationId, nextBefore } = state.current;
    const query = '?limit=50' + (nextBefore ? '&before=' + nextBefore : '');
    const page = await chatApi('/api/v1/conversations/' + conversationId + '/messages' + query);
    if (state.current?.conversationId !== conversationId) return;   // user switched chats meanwhile
    state.current.nextBefore = page.nextBefore;
    el('load-older').classList.toggle('hidden', !page.nextBefore);
    // page is newest-first; prepend in reverse so the list stays oldest-first
    const list = el('message-list');
    page.messages.forEach((msg) => list.prepend(bubble(msg, time(msg.sentAt))));
  }

  function appendMessage(msg) {
    if (document.querySelector(`#message-list [data-id="${msg.messageId}"]`)) return;   // de-duplicate
    el('message-list').appendChild(bubble(msg, time(msg.sentAt)));
  }

  function bubble(msg, metaText) {
    const li = document.createElement('li');
    li.className = 'bubble' + (msg.senderId === ME ? ' mine' : '');
    if (msg.messageId) li.dataset.id = msg.messageId;
    const text = document.createElement('span');
    text.textContent = msg.body;
    const meta = document.createElement('span');
    meta.className = 'meta';
    meta.textContent = metaText;
    li.append(text, meta);
    return li;
  }

  // ---------- search ----------
  let searchTimer;
  el('search').addEventListener('input', (e) => {
    clearTimeout(searchTimer);
    const query = e.target.value.trim();
    searchTimer = setTimeout(() => search(query), 250);   // debounce typing
  });

  async function search(query) {
    const results = el('search-results');
    if (!query) return results.replaceChildren();
    const res = await fetch('/api/v1/users?query=' + encodeURIComponent(query));
    const users = res.ok ? await res.json() : [];
    results.replaceChildren(...users.filter((u) => u.id !== ME).map((user) => {
      state.names.set(user.id, user.displayName);
      const li = document.createElement('li');
      li.textContent = `${user.displayName} (@${user.username})`;
      li.onclick = () => startConversation(user.id);
      return li;
    }));
  }

  async function startConversation(peerId) {
    const conversation = await chatApi('/api/v1/conversations', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ peerId }),
    });
    el('search').value = '';
    el('search-results').replaceChildren();
    if (!state.inbox.has(conversation.conversationId)) {
      state.inbox.set(conversation.conversationId, { conversationId: conversation.conversationId, peerId });
    }
    openConversation(conversation.conversationId, peerId);
  }

  // ---------- helpers ----------
  function time(iso) {
    return iso ? new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '';
  }

  function scrollToBottom() {
    const box = el('messages');
    box.scrollTop = box.scrollHeight;
  }

  function setStatus(text, cls) {
    const status = el('connection');
    status.textContent = text;
    status.className = 'status ' + cls;
  }

  el('composer').addEventListener('submit', (e) => {
    e.preventDefault();
    const input = el('message-input');
    const body = input.value.trim();
    if (body) {
      send(body);
      input.value = '';
    }
  });
  el('load-older').addEventListener('click', loadOlder);

  connect();
})();
