package com.example.urbanagent.query.application;

import com.example.urbanagent.query.application.dto.DataFieldView;
import com.example.urbanagent.query.application.dto.DataTableView;
import com.example.urbanagent.query.application.dto.MetricDefinitionView;
import com.example.urbanagent.query.domain.DataField;
import com.example.urbanagent.query.domain.DataTable;
import com.example.urbanagent.query.domain.MetricDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class QueryCatalogViewMapper {

    public MetricDefinitionView toView(MetricDefinition metricDefinition) {
        return new MetricDefinitionView(
                metricDefinition.getMetricCode(),
                metricDefinition.getMetricName(),
                metricDefinition.getDescription(),
                metricDefinition.getAggregationExpr(),
                metricDefinition.getDefaultTimeField(),
                metricDefinition.getCommonDimensions(),
                metricDefinition.getTableName(),
                metricDefinition.resolveCaliberVersion(),
                metricDefinition.resolveDataQuality(),
                metricDefinition.resolveApplicableRegion(),
                metricDefinition.resolveDataUpdatedAt()
        );
    }

    public DataTableView toView(DataTable dataTable, List<DataFieldView> fields) {
        return new DataTableView(
                dataTable.getTableName(),
                dataTable.getBusinessName(),
                dataTable.getPermissionTag(),
                dataTable.getRegionCode(),
                fields
        );
    }

    public DataFieldView toView(DataField dataField) {
        return new DataFieldView(
                dataField.getFieldName(),
                dataField.getBusinessName(),
                dataField.getDataType(),
                dataField.getSensitiveLevel().name().toLowerCase(Locale.ROOT)
        );
    }
}
