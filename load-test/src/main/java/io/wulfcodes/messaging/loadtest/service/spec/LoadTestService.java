package io.wulfcodes.messaging.loadtest.service.spec;

import io.wulfcodes.messaging.loadtest.model.vo.StageResult;

import java.util.List;

public interface LoadTestService {

    List<StageResult> run() throws Exception;
}
