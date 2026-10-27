package io.casehub.work.ai.config;

import io.casehub.work.ai.skill.CompositeSkillProfileProvider;
import io.casehub.work.api.SkillProfile;
import io.casehub.work.api.spi.SkillProfileProvider;
import io.quarkus.arc.InjectableBean;
import io.quarkus.arc.InstanceHandle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AiBeansTest {

    @Test
    @SuppressWarnings("unchecked")
    void composite_excludesItselfBeforeInstantiatingDelegates() {
        InstanceHandle<SkillProfileProvider> self = mock(InstanceHandle.class);
        InjectableBean<SkillProfileProvider> selfBean = mock(InjectableBean.class);
        when(self.getBean()).thenReturn(selfBean);
        when(selfBean.getTypes()).thenReturn(Set.of(CompositeSkillProfileProvider.class));

        InstanceHandle<SkillProfileProvider> delegate = mock(InstanceHandle.class);
        InjectableBean<SkillProfileProvider> delegateBean = mock(InjectableBean.class);
        when(delegate.getBean()).thenReturn(delegateBean);
        when(delegateBean.getTypes()).thenReturn(Set.of(SkillProfileProvider.class));
        when(delegate.get()).thenReturn((worker, capabilities) -> SkillProfile.ofNarrative("Legal specialist"));

        var composite = new AiBeans().compositeSkillProfileProvider(List.of(self, delegate));

        assertThat(composite.getProfile("alice", Set.of()).narrative()).isEqualTo("Legal specialist");
        verify(self, never()).get();
    }
}
