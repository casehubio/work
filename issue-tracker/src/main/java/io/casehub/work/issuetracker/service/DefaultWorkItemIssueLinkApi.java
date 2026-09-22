package io.casehub.work.issuetracker.service;

import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.work.api.spi.WorkItemIssueLinkApi;
import io.casehub.work.api.view.CreateIssueRequest;
import io.casehub.work.api.view.IssueLinkView;
import io.casehub.work.api.view.LinkIssueRequest;
import io.casehub.work.api.view.SyncLinksResult;
import io.casehub.work.issuetracker.model.WorkItemIssueLink;
import io.casehub.work.issuetracker.spi.IssueTrackerException;

@ApplicationScoped
public class DefaultWorkItemIssueLinkApi implements WorkItemIssueLinkApi {

    @Inject
    IssueLinkService linkService;

    @Override
    public IssueLinkView linkIssue(UUID workItemId, LinkIssueRequest request, String tenancyId) {
        if (request == null || request.trackerType() == null || request.externalRef() == null) {
            throw new IllegalArgumentException("trackerType and externalRef are required");
        }
        try {
            final WorkItemIssueLink link = linkService.linkExistingIssue(
                    workItemId,
                    request.trackerType(),
                    request.externalRef(),
                    request.linkedBy() != null ? request.linkedBy() : "unknown");
            return toView(link);
        } catch (IssueTrackerException e) {
            if (e.isNotFound()) {
                return null;
            }
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public IssueLinkView createAndLink(UUID workItemId, CreateIssueRequest request, String tenancyId) {
        if (request == null || request.trackerType() == null || request.title() == null) {
            throw new IllegalArgumentException("trackerType and title are required");
        }
        try {
            final WorkItemIssueLink link = linkService.createAndLink(
                    workItemId,
                    request.trackerType(),
                    request.title(),
                    request.body() != null ? request.body() : "",
                    request.linkedBy() != null ? request.linkedBy() : "unknown");
            return toView(link);
        } catch (IssueTrackerException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public List<IssueLinkView> listLinks(UUID workItemId, String tenancyId) {
        return linkService.listLinks(workItemId).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    public void removeLink(UUID workItemId, UUID linkId, String tenancyId) {
        if (!linkService.removeLink(linkId, workItemId)) {
            throw new IllegalArgumentException("Link not found");
        }
    }

    @Override
    public SyncLinksResult syncLinks(UUID workItemId, String tenancyId) {
        final int synced = linkService.syncLinks(workItemId);
        return new SyncLinksResult(synced, workItemId);
    }

    private IssueLinkView toView(WorkItemIssueLink link) {
        return new IssueLinkView(
                link.id, link.workItemId, link.trackerType, link.externalRef,
                link.title != null ? link.title : "",
                link.url != null ? link.url : "",
                link.status, link.linkedAt, link.linkedBy);
    }
}
