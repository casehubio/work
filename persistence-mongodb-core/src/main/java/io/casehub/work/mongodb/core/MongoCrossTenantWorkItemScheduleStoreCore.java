package io.casehub.work.mongodb.core;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.casehub.work.mongodb.core.doc.MongoWorkItemScheduleDocument;
import io.casehub.work.runtime.model.WorkItemSchedule;
import io.casehub.work.runtime.repository.CrossTenantWorkItemScheduleStore;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class MongoCrossTenantWorkItemScheduleStoreCore implements CrossTenantWorkItemScheduleStore {

    private final MongoCollection<MongoWorkItemScheduleDocument> collection;

    public MongoCrossTenantWorkItemScheduleStoreCore(MongoDatabase database) {
        this.collection = database.getCollection("work_item_schedules", MongoWorkItemScheduleDocument.class);
    }

    @Override
    public List<WorkItemSchedule> findActive() {
        Document filter = new Document("active", true);

        return collection.find(filter).into(new ArrayList<>())
                .stream()
                .map(MongoWorkItemScheduleDocument::toDomain)
                .toList();
    }
}
