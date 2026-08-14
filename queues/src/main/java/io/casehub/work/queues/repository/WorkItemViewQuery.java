package io.casehub.work.queues.repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.casehub.work.runtime.model.WorkItemEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;

import io.casehub.platform.api.view.SubjectViewQuery;
import io.casehub.platform.api.view.SubjectViewSpec;
import io.casehub.platform.view.jpa.LabelPatternPredicates;
import io.casehub.work.api.WorkItemSummary;
import io.casehub.work.runtime.model.WorkItemLabelEntity;
import io.casehub.work.runtime.service.SummaryQueryBuilder;

@ApplicationScoped
public class WorkItemViewQuery implements SubjectViewQuery<WorkItemEntity> {

    @Inject
    EntityManager em;

    @Override
    public List<WorkItemEntity> findByView(SubjectViewSpec view) {
        CriteriaBuilder                     cb        = em.getCriteriaBuilder();
        CriteriaQuery<WorkItemEntity>       cq        = cb.createQuery(WorkItemEntity.class);
        Root<WorkItemEntity>                      root      = cq.from(WorkItemEntity.class);
        Join<WorkItemEntity, WorkItemLabelEntity> labelJoin = root.join("labels");

        cq.where(cb.and(
                LabelPatternPredicates.toPredicate(cb, labelJoin.get("path"), view.labelPattern()),
                cb.equal(root.get("tenancyId"), view.tenancyId())
        )).distinct(true);

        applySorting(cb, cq, root, view);
        return em.createQuery(cq).getResultList();
    }

    @Override
    public List<WorkItemEntity> findByView(SubjectViewSpec view, int offset, int limit) {
        CriteriaBuilder                     cb        = em.getCriteriaBuilder();
        CriteriaQuery<WorkItemEntity>       cq        = cb.createQuery(WorkItemEntity.class);
        Root<WorkItemEntity>                      root      = cq.from(WorkItemEntity.class);
        Join<WorkItemEntity, WorkItemLabelEntity> labelJoin = root.join("labels");

        cq.where(cb.and(
                LabelPatternPredicates.toPredicate(cb, labelJoin.get("path"), view.labelPattern()),
                cb.equal(root.get("tenancyId"), view.tenancyId())
        )).distinct(true);

        applySorting(cb, cq, root, view);

        TypedQuery<WorkItemEntity> query = em.createQuery(cq);
        query.setFirstResult(offset);
        query.setMaxResults(limit);
        return query.getResultList();
    }

    @Override
    public long countByView(SubjectViewSpec view) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long>                 cq        = cb.createQuery(Long.class);
        Root<WorkItemEntity>                      root      = cq.from(WorkItemEntity.class);
        Join<WorkItemEntity, WorkItemLabelEntity> labelJoin = root.join("labels");

        cq.select(cb.countDistinct(root));
        cq.where(cb.and(
                LabelPatternPredicates.toPredicate(cb, labelJoin.get("path"), view.labelPattern()),
                cb.equal(root.get("tenancyId"), view.tenancyId())
        ));

        return em.createQuery(cq).getSingleResult();
    }

    public WorkItemSummary summarizeByView(final SubjectViewSpec view, final Instant now) {
        final Map<String, Object> params = new HashMap<>();
        params.put("tenancyId", view.tenancyId());

        final String labelJpql = buildLabelJpql(view.labelPattern(), params);
        final String baseJpql = "FROM WorkItemEntity wi JOIN wi.labels l WHERE wi.tenancyId = :tenancyId AND " + labelJpql;
        return SummaryQueryBuilder.build(em, baseJpql, params, true, now);
    }

    private String buildLabelJpql(final String pattern, final Map<String, Object> params) {
        if (pattern.endsWith("/**")) {
            final String prefix = pattern.substring(0, pattern.length() - 3) + "/";
            params.put("labelPattern", prefix + "%");
            return "l.path LIKE :labelPattern";
        }
        if (pattern.endsWith("/*")) {
            final String prefix = pattern.substring(0, pattern.length() - 2) + "/";
            params.put("labelPattern", prefix + "%");
            params.put("labelPatternDeep", prefix + "%/%");
            return "l.path LIKE :labelPattern AND l.path NOT LIKE :labelPatternDeep";
        }
        params.put("labelPattern", pattern);
        return "l.path = :labelPattern";
    }

    private void applySorting(CriteriaBuilder cb, CriteriaQuery<WorkItemEntity> cq,
                              Root<WorkItemEntity> root, SubjectViewSpec view) {
        if (view.sortField() != null) {
            cq.orderBy("DESC".equalsIgnoreCase(view.sortDirection())
                    ? cb.desc(root.get(view.sortField()))
                    : cb.asc(root.get(view.sortField())));
        } else {
            cq.orderBy(cb.asc(root.get("id")));
        }
    }
}
