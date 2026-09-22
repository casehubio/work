# io.casehub.work.api.spi.WorkItemLabelRuleApi

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

## Methods

### `public abstract io.casehub.work.api.view.LabelRuleView create(io.casehub.work.api.view.CreateLabelRuleRequest request, java.lang.String tenancyId)`

#### Parameters

- `request` (`io.casehub.work.api.view.CreateLabelRuleRequest`)
- `tenancyId` (`java.lang.String`)

### `public abstract void delete(java.util.UUID ruleId, java.lang.String tenancyId)`

#### Parameters

- `ruleId` (`java.util.UUID`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.EvaluateExpressionResult evaluate(io.casehub.work.api.view.EvaluateExpressionRequest request, java.lang.String tenancyId)`

#### Parameters

- `request` (`io.casehub.work.api.view.EvaluateExpressionRequest`)
- `tenancyId` (`java.lang.String`)

### `public abstract java.util.List<io.casehub.work.api.view.LabelRuleView> list(java.lang.String tenancyId)`

#### Parameters

- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.LabelRuleView update(java.util.UUID ruleId, io.casehub.work.api.view.CreateLabelRuleRequest request, java.lang.String tenancyId)`

#### Parameters

- `ruleId` (`java.util.UUID`)
- `request` (`io.casehub.work.api.view.CreateLabelRuleRequest`)
- `tenancyId` (`java.lang.String`)
