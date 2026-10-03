package com.telecom.orders.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/** Named, bounded pools. A full pool throws TaskRejectedException (-> 503 + Retry-After). */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    /** Downstream calls and the persisting stage of the order workflow. Default @Async executor. */
    @Bean(name = "integrationExecutor")
    public ThreadPoolTaskExecutor integrationExecutor() {
        return pool("integration-", 8, 32, 200);
    }

    /** Spring MVC executor (Callable / StreamingResponseBody), configured in WebAsyncConfig. */
    @Bean(name = "mvcAsyncExecutor")
    public ThreadPoolTaskExecutor mvcAsyncExecutor() {
        return pool("mvc-async-", 4, 16, 100);
    }

    /** Simulated provisioning callbacks. */
    @Bean(name = "provisioningExecutor")
    public ThreadPoolTaskExecutor provisioningExecutor() {
        return pool("provisioning-", 2, 8, 100);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Override
    public Executor getAsyncExecutor() {
        return integrationExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> log.error("@Async {} failed", method.getName(), ex);
    }

    private static ThreadPoolTaskExecutor pool(String prefix, int core, int max, int queue) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(prefix);
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
