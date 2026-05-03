package com.example.urbanagent.query.application;

import com.example.urbanagent.common.error.BusinessException;
import com.example.urbanagent.common.error.ErrorCode;
import com.example.urbanagent.query.domain.DataSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 业务数据适配器注册中心。
 *
 * <p>统一管理系统中所有 {@link BusinessDataAdapter} 实现，
 * 根据数据源类型自动路由到对应适配器。
 *
 * <p>设计原则：
 * <ul>
 *   <li>启动时自动收集所有 {@link BusinessDataAdapter} 实现</li>
 *   <li>按数据源类型（DataSourceType）建立索引，支持 O(1) 查找</li>
 *   <li>同一类型可注册多个适配器，按优先级选择</li>
 *   <li>未找到适配器时抛出明确的业务异常</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 * BusinessDataConnectorRegistry registry;
 * BusinessDataAdapter adapter = registry.resolve(dataSource);
 * List&lt;Map&lt;String, Object&gt;&gt; rows = adapter.execute(request);
 * </pre>
 */
@Component
public class BusinessDataConnectorRegistry {

    private final Map<String, List<BusinessDataAdapter>> adaptersByType = new ConcurrentHashMap<>();
    private final List<BusinessDataAdapter> universalAdapters = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * 注册单个适配器，按其支持的数据源类型建立索引。
     * 由 Spring 在初始化时自动调用所有标注 @Bean 的 BusinessDataAdapter。
     *
     * @param adapter 业务数据适配器实现
     */
    public void register(BusinessDataAdapter adapter) {
        for (String typeCode : supportedTypeCodes(adapter)) {
            adaptersByType
                    .computeIfAbsent(typeCode.toUpperCase(Locale.ROOT), k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                    .add(adapter);
        }
        // 通用适配器（supports 始终返回 true）
        if (universalAdapters.stream().noneMatch(a -> a.getClass() == adapter.getClass())) {
            universalAdapters.add(adapter);
        }
    }

    /**
     * 根据数据源查找最合适的适配器。
     *
     * @param dataSource 目标数据源（不能为空）
     * @return 支持该数据源的适配器
     * @throws BusinessException 当未找到适配器时抛出
     */
    public BusinessDataAdapter resolve(DataSource dataSource) {
        if (dataSource == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "数据源不能为空");
        }
        String typeCode = dataSource.getType().name().toUpperCase(Locale.ROOT);
        List<BusinessDataAdapter> candidates = adaptersByType.get(typeCode);
        if (candidates != null && !candidates.isEmpty()) {
            return candidates.get(0);
        }
        // 回退到通用适配器
        for (BusinessDataAdapter adapter : universalAdapters) {
            if (adapter.supports(dataSource)) {
                return adapter;
            }
        }
        throw new BusinessException(
                ErrorCode.BAD_REQUEST,
                String.format("当前数据源类型 [%s] 未注册适配器", dataSource.getType())
        );
    }

    /**
     * 列出当前注册的所有适配器（调试用）。
     */
    public List<BusinessDataAdapter> allAdapters() {
        return adaptersByType.values().stream()
                .flatMap(List::stream)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 检查指定类型是否有适配器。
     */
    public boolean hasAdapterFor(String dataSourceType) {
        List<BusinessDataAdapter> candidates = adaptersByType.get(dataSourceType.toUpperCase(Locale.ROOT));
        return (candidates != null && !candidates.isEmpty())
                || universalAdapters.stream().anyMatch(a -> true);
    }

    private String[] supportedTypeCodes(BusinessDataAdapter adapter) {
        try {
            java.lang.reflect.Method method = adapter.getClass().getMethod("supportedTypes");
            Object result = method.invoke(adapter);
            if (result instanceof String[] arr) return arr;
            if (result instanceof List) return ((List<?>) result).toArray(new String[0]);
        } catch (Exception ignored) {
            // 无 supportedTypes 方法，按 BusinessDataAdapter.supports() 动态判断
        }
        return new String[0];
    }
}