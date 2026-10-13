package com.dockerportal.audit;

import com.dockerportal.model.enums.AuditLevel;
import com.dockerportal.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * AuditAspect 单测：用 AspectJProxyFactory 织入真实切面，Mockito 断言落库参数。
 * 重点验证 SpEL 取 resourceId / environmentId、成功与异常两条路径、分级透传。
 */
class AuditAspectTest {

    private AuditService auditService;
    private Sample proxy;

    static class Sample {
        @Auditable(action = "CONTAINER_REMOVE", resourceType = "container",
                level = AuditLevel.CRITICAL, resourceId = "#containerId", environmentId = "#envId")
        public String remove(Long envId, String containerId) {
            return "removed:" + containerId;
        }

        @Auditable(action = "BOOM", resourceType = "x", level = AuditLevel.SECURITY)
        public void boom() {
            throw new IllegalStateException("kaput");
        }
    }

    @BeforeEach
    void setUp() {
        auditService = mock(AuditService.class);
        AspectJProxyFactory pf = new AspectJProxyFactory(new Sample());
        pf.addAspect(new AuditAspect(auditService));
        proxy = pf.getProxy();
    }

    @Test
    void recordsSuccessWithSpelResolvedIds() {
        proxy.remove(7L, "abc123");

        ArgumentCaptor<String> resourceId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> envId = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<AuditLevel> level = ArgumentCaptor.forClass(AuditLevel.class);
        verify(auditService).recordFull(
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq("CONTAINER_REMOVE"),
                org.mockito.ArgumentMatchers.eq("container"), resourceId.capture(), envId.capture(),
                level.capture(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyLong());

        assertThat(resourceId.getValue()).isEqualTo("abc123");
        assertThat(envId.getValue()).isEqualTo(7L);
        assertThat(level.getValue()).isEqualTo(AuditLevel.CRITICAL);
    }

    @Test
    void recordsFailureAndRethrows() {
        assertThatThrownBy(() -> proxy.boom())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("kaput");

        ArgumentCaptor<String> err = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> success = ArgumentCaptor.forClass(Boolean.class);
        verify(auditService).recordFull(
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq("BOOM"),
                org.mockito.ArgumentMatchers.eq("x"), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(AuditLevel.SECURITY),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.isNull(),
                success.capture(), err.capture(), org.mockito.ArgumentMatchers.anyLong());

        assertThat(success.getValue()).isFalse();
        assertThat(err.getValue()).contains("IllegalStateException").contains("kaput");
    }
}
