# HANDOFF — casehub-pages (issue-413)

## Session Summary

All 10 tasks for #413 (Broaden SPI interfaces for chart, map, and graph components) complete. Batches 1-5 implemented, squashed, and pushed to origin. GitHub issue closed. Design specs promoted to project docs. SPI Broadening showcase added with 5 working tabs. Graph Editing showcases fixed for tag rename.

## Branch State

- **Project branch:** `issue-413-broaden-spi-interfaces` — on origin (`casehubio/casehub-pages`), 3 squashed feat commits + 1 docs promote + 1 showcase trim
- **GitHub issue:** #413 — CLOSED
- **Not yet merged to main** — needs work-end merge or manual merge

## What Was Built

1. **Interface hierarchy** — extracted `ChartSettingsBase` from `ChartSettings`, re-parented non-Cartesian charts
2. **Base class refactoring** — widened PagesChartElement, refactored `applyChartSettings`, centralised escape hatch merging
3. **Shared SPI properties** — tooltip, animation, color, backgroundColor, legend orient/selectedMode
4. **Cartesian promotions** — axis type/min/max/inverse, zoom evolution
5. **Per-chart series** — bar, line, pie, scatter, heatmap, treemap, meter, timeseries properties
6. **Component-specific** — MapProps, GraphProps, DensityHeatmapProps broadening + radius wiring fix
7. **Typed escape hatches** — CasehubEChartsExtension, CasehubHeatmapExtension, CasehubReactFlowExtension, CasehubElkExtension
8. **Schema regeneration** — 56 Zod schemas, staleness test passes
9. **GraphCanvasProps** — interface + 7-point registration (ComponentTypeRegistry, TYPE_MAP, schema registry, exports, DATA_COMPONENT_TYPES in activation.ts and component-desugar.ts)
10. **PagesGraphCanvas YAML bridge** — DataSourceController wiring, data-to-model mapping, algorithm translation, height/width style application, connectionsEnabled defaults to false

## Showcase

- **SPI Broadening** (`examples/samples/Charts/SPI Broadening.dash.yaml`) — 5 tabs: Graph Canvas, Shared Properties, Per-Chart Series, Cartesian Axis, Escape Hatches
- **Graph Editing** — 3 showcases fixed: `pages-graph-canvas` → `graph-canvas-core` (open + close tags)

## Cross-Repo Commits (from soredium session, 2026-11-23)

Committed to engine, eidos, and agency as part of Hortora/soredium#627 (CI fork divergence fix):

| Repo | Branch | Commit | What |
|------|--------|--------|------|
| engine | issue-1351-agent-task-dispatch-test | c415ec2ac | fix(ci): gate API docs generation on repository owner |
| eidos | issue-266-cross-unit-evidence-threshold | f89da1e | fix(ci): gate API docs generation on repository owner |
| agency | main | b5b33429 | fix(ci): gate API docs generation on repository owner |

These add `if: github.repository_owner == 'casehubio'` to `generate-api-docs.yml` to prevent the CI action from committing to forks, which was creating parallel commits that broke mirror pushes.

**Note:** engine and eidos commits landed on working branches (not main). They need to be landed via work-end or cherry-picked to main to take effect on GitHub.

## What's Next

- Merge `issue-413-broaden-spi-interfaces` to main via work-end
- Land the CI fix commits on engine and eidos (currently on working branches)
