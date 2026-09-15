package io.casehub.work.rest.service;

import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.casehub.work.api.spi.WorkItemLinkApi;
import io.casehub.work.api.view.AddLinkRequest;
import io.casehub.work.api.view.WorkItemLinkView;
import io.casehub.work.runtime.model.WorkItemLink;
import io.casehub.work.runtime.repository.WorkItemLinkStore;

@ApplicationScoped
public class DefaultWorkItemLinkApi implements WorkItemLinkApi {

    @Inject
    WorkItemLinkStore linkStore;

    @Override
    @Transactional
    public WorkItemLinkView addLink(UUID workItemId, AddLinkRequest body, String tenancyId) {
        WorkItemLink link = new WorkItemLink();
        link.workItemId = workItemId;
        link.url = body.url();
        link.title = body.title();
        link.relationType = body.relationType();
        link.linkedBy = "system";
        link.tenancyId = tenancyId;
        return ViewMapper.toLinkView(linkStore.put(link));
    }

    @Override
    public List<WorkItemLinkView> listLinks(UUID workItemId, String tenancyId) {
        return linkStore.findByWorkItemId(workItemId).stream()
                .map(ViewMapper::toLinkView)
                .toList();
    }

    @Override
    @Transactional
    public void deleteLink(UUID workItemId, UUID linkId, String tenancyId) {
        linkStore.delete(linkId);
    }
}
