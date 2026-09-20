package io.casehub.work.mongodb.core;

import io.casehub.work.runtime.model.WorkItemTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MongoWorkItemTemplateStoreCoreTest extends MongoStoreTestBase {

    MongoWorkItemTemplateStoreCore store;

    @BeforeEach
    void setUp() {
        store = new MongoWorkItemTemplateStoreCore(database, principal("tenant1", "actor1"));
    }

    @Test
    void putAndGet() {
        WorkItemTemplate t = new WorkItemTemplate();
        t.name = "test-template";
        store.put(t);
        assertThat(t.id).isNotNull();

        var found = store.get(t.id);
        assertThat(found).isPresent();
        assertThat(found.get().name).isEqualTo("test-template");
    }

    @Test
    void getFiltersByTenant() {
        WorkItemTemplate t = new WorkItemTemplate();
        t.name = "template-a";
        store.put(t);

        var otherTenantStore = new MongoWorkItemTemplateStoreCore(
                database, principal("other-tenant", "actor2"));
        assertThat(otherTenantStore.get(t.id)).isEmpty();
    }

    @Test
    void deleteRemovesDocument() {
        WorkItemTemplate t = new WorkItemTemplate();
        t.name = "to-delete";
        store.put(t);
        assertThat(store.delete(t.id)).isTrue();
        assertThat(store.get(t.id)).isEmpty();
    }

    @Test
    void scanAllReturnsSorted() {
        WorkItemTemplate b = new WorkItemTemplate();
        b.name = "beta";
        store.put(b);
        WorkItemTemplate a = new WorkItemTemplate();
        a.name = "alpha";
        store.put(a);

        var all = store.scanAll();
        assertThat(all).hasSize(2);
        assertThat(all.get(0).name).isEqualTo("alpha");
    }

    @Test
    void getByNameFindsTemplate() {
        WorkItemTemplate t = new WorkItemTemplate();
        t.name = "named-template";
        store.put(t);

        var found = store.getByName("named-template");
        assertThat(found).isPresent();
        assertThat(found.get().id).isEqualTo(t.id);
    }

    @Test
    void getByNameFiltersByTenant() {
        WorkItemTemplate t = new WorkItemTemplate();
        t.name = "tenant-specific";
        store.put(t);

        var otherTenantStore = new MongoWorkItemTemplateStoreCore(
                database, principal("other-tenant", "actor2"));
        assertThat(otherTenantStore.getByName("tenant-specific")).isEmpty();
    }
}
