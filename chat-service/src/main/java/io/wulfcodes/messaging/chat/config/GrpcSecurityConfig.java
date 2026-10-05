package io.wulfcodes.messaging.chat.config;

import io.wulfcodes.messaging.chat.grpc.ClusterTokenClientInterceptor;
import io.wulfcodes.messaging.chat.grpc.ClusterTokenServerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GlobalClientInterceptor;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.grpc.server.security.AuthenticationProcessInterceptor;
import org.springframework.grpc.server.security.GrpcSecurity;

/**
 * Security for the internal gRPC port.
 * Spring gRPC secures the gRPC server with Spring Security by default (expecting user
 * credentials such as a JWT). Node-to-node calls carry no user identity, so the Spring Security
 * layer permits them and the cluster-token interceptor does the actual authentication.
 */
@Configuration
public class GrpcSecurityConfig {

    @Bean
    public AuthenticationProcessInterceptor grpcAuthorizationPolicy(GrpcSecurity grpc) throws Exception {
        return grpc.authorizeRequests(requests -> requests.allRequests().permitAll()).build();
    }

    @Bean
    @GlobalServerInterceptor
    public ClusterTokenServerInterceptor clusterTokenServerInterceptor(ChatProperties properties) {
        return new ClusterTokenServerInterceptor(properties);
    }

    @Bean
    @GlobalClientInterceptor
    public ClusterTokenClientInterceptor clusterTokenClientInterceptor(ChatProperties properties) {
        return new ClusterTokenClientInterceptor(properties);
    }
}
