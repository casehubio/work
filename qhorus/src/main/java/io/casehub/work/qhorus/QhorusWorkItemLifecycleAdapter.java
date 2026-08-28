package io.casehub.work.qhorus;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.work.api.WorkEventType;
import io.casehub.work.api.WorkItemStatusEvent;
import io.casehub.work.api.spi.WorkItemObserver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class QhorusWorkItemLifecycleAdapter implements WorkItemObserver {

    private static final Logger LOG = Logger.getLogger(QhorusWorkItemLifecycleAdapter.class);

    @Inject
    MessageDispatcher messageDispatcher;

    @Override
    public void onStatusChange(final WorkItemStatusEvent event) {
        try {
            if (!QhorusRef.isQhorus(event.callerRef())) {
                return;
            }
            if (!event.status().isTerminal()) {
                return;
            }

            final QhorusRef   ref       = QhorusRef.parse(event.callerRef());
            final MessageType speechAct = mapToSpeechAct(event.eventType());
            if (speechAct == null) {
                return;
            }

            final String content = buildContent(event);

            messageDispatcher.dispatch(MessageDispatch.builder()
                    .channelId(ref.channelId())
                    .sender("workitems")
                    .type(speechAct)
                    .correlationId(ref.correlationId())
                    .inReplyTo(ref.messageId())
                    .content(content)
                    .actorType(ActorType.SYSTEM)
                    .tenancyId(event.tenancyId())
                    .build());

        } catch (final Exception ex) {
            LOG.warnf(ex, "Qhorus channel post failed for callerRef=%s workItemId=%s",
                    event.callerRef(), event.workItemId());
        }
    }

    static MessageType mapToSpeechAct(final WorkEventType eventType) {
        return switch (eventType) {
            case COMPLETED -> MessageType.DONE;
            case REJECTED, FAULTED, ESCALATED -> MessageType.FAILURE;
            case CANCELLED, EXPIRED, OBSOLETE -> MessageType.DECLINE;
            default -> null;
        };
    }

    private static String buildContent(final WorkItemStatusEvent event) {
        return "{\"workItemId\":\"" + event.workItemId()
                + "\",\"status\":\"" + event.status()
                + "\",\"outcome\":" + jsonString(event.outcome())
                + ",\"resolution\":" + jsonString(event.detail())
                + ",\"assigneeId\":" + jsonString(event.assigneeId()) + "}";
    }

    private static String jsonString(final String value) {
        return value == null ? "null" : "\"" + value.replace("\"", "\\\"") + "\"";
    }
}
