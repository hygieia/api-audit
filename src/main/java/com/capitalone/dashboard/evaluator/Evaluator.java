package com.capitalone.dashboard.evaluator;

import com.capitalone.dashboard.ApiSettings;
import com.capitalone.dashboard.misc.HygieiaException;
import com.capitalone.dashboard.model.Dashboard;
import com.capitalone.dashboard.model.AuditException;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.CollectorType;
import com.capitalone.dashboard.model.Widget;
import com.capitalone.dashboard.model.Component;
import com.capitalone.dashboard.repository.CollectorItemRepository;
import com.capitalone.dashboard.repository.ComponentRepository;
import com.capitalone.dashboard.repository.DashboardRepository;
import com.google.common.collect.Lists;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Collections;
import java.util.stream.Collectors;

public abstract class Evaluator<T> {

    public static final String TEST_TYPE = "testType";
    @Autowired
    protected ComponentRepository componentRepository;

    @Autowired
    protected DashboardRepository dashboardRepository;

    @Autowired
    protected CollectorItemRepository collectorItemRepository;

    @Autowired
    protected ApiSettings settings;

    public abstract Collection<T> evaluate(Dashboard dashboard, long beginDate, long endDate, Map<?, ?> data) throws AuditException;

    public abstract T evaluate(CollectorItem collectorItem, long beginDate, long endDate, Map<?, ?> data) throws AuditException, HygieiaException;

    /**
     * @param dashboard the dashboard
     * @param collectorType the collector type
     * @return list of @CollectorItem for a given dashboard, widget name and collector type
     */
    List<CollectorItem> getCollectorItems(Dashboard dashboard, CollectorType collectorType) {
        Optional<ObjectId> componentIdOpt = dashboard.getWidgets().stream().findFirst().map(Widget::getComponentId);
        Optional<Component> componentOpt = componentIdOpt.isPresent() ? Optional.ofNullable(componentRepository.findOne(componentIdOpt.get())) : Optional.empty();
        // This collector items from component is stale. So, need the id's to look up current state of collector items.
        List<ObjectId> collectorItemIds = componentOpt.map(component ->
                component.getCollectorItems(collectorType).stream().map(CollectorItem::getId).collect(Collectors.toList())).orElse(Collections.emptyList());
        return CollectionUtils.isNotEmpty(collectorItemIds) ? IterableUtils.toList(collectorItemRepository.findAll(collectorItemIds)) : Collections.emptyList();
    }

    List<CollectorItem> getCollectorItems(Dashboard dashboard, CollectorType collectorType, String testType) {
        Optional<ObjectId> componentIdOpt = dashboard.getWidgets().stream().findFirst().map(Widget::getComponentId);
        Optional<Component> componentOpt = componentIdOpt.isPresent() ? Optional.ofNullable(componentRepository.findOne(componentIdOpt.get())) : Optional.empty();
        // This collector items from component is stale. So, need the id's to look up current state of collector items.
        List<ObjectId> collectorItemIds = componentOpt.map(component ->
                component.getCollectorItems(collectorType).stream().filter(c -> isEqualsTestType(c,testType)).map(CollectorItem::getId).collect(Collectors.toList())).orElse(Collections.emptyList());
        return CollectionUtils.isNotEmpty(collectorItemIds) ? IterableUtils.toList(collectorItemRepository.findAll(collectorItemIds)) : Collections.emptyList();
    }

    private boolean isEqualsTestType(CollectorItem c,String testType) {
        if(Objects.isNull(c.getOptions().get(TEST_TYPE))) return false;
        return c.getOptions().get(TEST_TYPE).equals(testType);
    }


    public Dashboard getDashboard(String businessService, String businessComponent) {
        return dashboardRepository.findByConfigurationItemBusServNameIgnoreCaseAndConfigurationItemBusAppNameIgnoreCase(businessService, businessComponent);
    }
}
