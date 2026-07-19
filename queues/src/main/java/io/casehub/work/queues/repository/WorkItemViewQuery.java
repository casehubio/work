package io.casehub.work.queues.repository;

import java.util.List;

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
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemLabel;

@ApplicationScoped
public class WorkItemViewQuery implements SubjectViewQuery<WorkItem> {

    @Inject
    EntityManager em;

    @Override
    public List<WorkItem> findByView(SubjectViewSpec view) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<WorkItem> cq = cb.createQuery(WorkItem.class);
        Root<WorkItem> root = cq.from(WorkItem.class);
        Join<WorkItem, WorkItemLabel> labelJoin = root.join("labels");

        cq.where(cb.and(
                LabelPatternPredicates.toPredicate(cb, labelJoin.get("path"), view.labelPattern()),
                cb.equal(root.get("tenancyId"), view.tenancyId())
        )).distinct(true);

        applySorting(cb, cq, root, view);
        return em.createQuery(cq).getResultList();
    }

    @Override
    public List<WorkItem> findByView(SubjectViewSpec view, int offset, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<WorkItem> cq = cb.createQuery(WorkItem.class);
        Root<WorkItem> root = cq.from(WorkItem.class);
        Join<WorkItem, WorkItemLabel> labelJoin = root.join("labels");

        cq.where(cb.and(
                LabelPatternPredicates.toPredicate(cb, labelJoin.get("path"), view.labelPattern()),
                cb.equal(root.get("tenancyId"), view.tenancyId())
        )).distinct(true);

        applySorting(cb, cq, root, view);

        TypedQuery<WorkItem> query = em.createQuery(cq);
        query.setFirstResult(offset);
        query.setMaxResults(limit);
        return query.getResultList();
    }

    @Override
    public long countByView(SubjectViewSpec view) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<WorkItem> root = cq.from(WorkItem.class);
        Join<WorkItem, WorkItemLabel> labelJoin = root.join("labels");

        cq.select(cb.countDistinct(root));
        cq.where(cb.and(
                LabelPatternPredicates.toPredicate(cb, labelJoin.get("path"), view.labelPattern()),
                cb.equal(root.get("tenancyId"), view.tenancyId())
        ));

        return em.createQuery(cq).getSingleResult();
    }

    private void applySorting(CriteriaBuilder cb, CriteriaQuery<WorkItem> cq,
            Root<WorkItem> root, SubjectViewSpec view) {
        if (view.sortField() != null) {
            cq.orderBy("DESC".equalsIgnoreCase(view.sortDirection())
                    ? cb.desc(root.get(view.sortField()))
                    : cb.asc(root.get(view.sortField())));
        } else {
            cq.orderBy(cb.asc(root.get("id")));
        }
    }
}
