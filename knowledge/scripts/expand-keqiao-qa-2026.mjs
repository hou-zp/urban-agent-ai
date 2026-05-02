import { readFile, writeFile } from "node:fs/promises";
import path from "node:path";

const rootDir = "/Users/houzp/Developer/projects/learning/ai-agent";
const importDir = path.join(rootDir, "knowledge/imports/2026-05-01-keqiao");
const manifestPath = path.join(importDir, "manifest.tsv");

const roles = [
  { key: "user", label: "群众咨询", hint: "群众侧答复要直接说明已知事实、还需补什么信息以及下一步怎么处理。" },
  { key: "accept", label: "坐席受理", hint: "受理时要把关键信息写进工单摘要，避免后续出现无法执行的空单。" },
  { key: "inspect", label: "巡查核实", hint: "现场核实时先抓影响判断的关键点，证据要能支撑后续派单和复查。" },
  { key: "handle", label: "处置推进", hint: "推进阶段要把要求写成可执行动作、责任主体和完成时点。" },
  { key: "review", label: "复盘长效", hint: "复盘阶段要沉淀为高发点位、共性原因和长效规则。" }
];

const scenes = [
  {
    name: "市容环境",
    typicalPlaces: "主次干道、背街小巷、商业街区、广场公园和公共空间",
    coreCheck: "问题位置、表现形态、影响范围、责任区边界和是否影响通行安全",
    responsible: "属地镇街、责任区单位或直接管理单位",
    basis: "绍兴市市容和环境卫生管理规定、浙江省城市市容和环境卫生管理条例",
    temporaryAction: "先清障、清理、提醒或设置警示，避免问题继续扩大",
    evidence: "全景照、近景照、参照物、时间点、责任主体线索和前后对比材料",
    risks: "通行受阻、环境脏乱、投诉升级和责任争议",
    primaryUnits: "属地镇街、综合执法、环卫保洁",
    supportUnits: "公安、住建、市政、社区物业等相关单位",
    longTerm: "后续宜纳入责任区巡查、重点点位台账和复发复盘，减少同类问题反复出现。",
    replyTone: "答复时先说明已安排核实和处置，再交代责任边界与复查安排。",
    metrics: "数量趋势、处置时长、超期率、复发率、重点点位分布和群众评价",
    boundary: "把所有公共空间问题笼统写成“环境差”而不区分具体类别和责任主体"
  },
  {
    name: "乱堆物堆料",
    typicalPlaces: "道路两侧、商铺门前、公共场地、空闲地块边缘和工地周边",
    coreCheck: "堆放物性质、占用范围、持续时间、是否影响通行和是否有临时占用手续",
    responsible: "堆放行为人、责任区单位、属地镇街或施工管理单位",
    basis: "浙江省城市市容和环境卫生管理条例、绍兴市市容和环境卫生管理规定",
    temporaryAction: "先督促清移、压缩占用范围或设置警示，必要时先行排障",
    evidence: "堆放位置、数量体积、物料种类、占道范围、责任主体线索和整改前后对比",
    risks: "影响通行、消防隐患、反复回潮和责任推诿",
    primaryUnits: "属地镇街、综合执法、责任区单位",
    supportUnits: "公安、消防、住建、社区物业等相关单位",
    longTerm: "对高发点位要同步做责任提醒、巡查加密和源头约束，不能只靠一次清运。",
    replyTone: "答复时重点讲清有没有占道、是否已通知清理、何时复查。",
    metrics: "高发路段、责任主体类型、当场整改率、复发率和超期件数量",
    boundary: "把临时合法堆放和长期无序堆放混为一谈"
  },
  {
    name: "积存垃圾渣土",
    typicalPlaces: "工地周边、道路边角、空闲地块、河岸附近和待建区域",
    coreCheck: "垃圾或渣土类型、来源线索、积存时长、清运责任和是否涉嫌违法倾倒",
    responsible: "施工单位、责任区单位、属地镇街或直接产生单位",
    basis: "绍兴市市容和环境卫生管理规定、柯桥区建设工程文明施工管理细则、建筑垃圾备案与电子联单口径",
    temporaryAction: "先圈定范围、压缩污染扩散并组织清运，必要时同步追溯来源",
    evidence: "垃圾渣土类别、体量、位置、运输痕迹、责任主体线索和清运记录",
    risks: "扬尘污染、偷倒反复、河道二次污染和安全隐患",
    primaryUnits: "属地镇街、综合执法、环卫保洁、住建相关单位",
    supportUnits: "公安、生态环境、水利、社区物业等相关单位",
    longTerm: "要把易偷倒点位纳入夜巡和联动治理，同时加强工地出入口和运输闭环管理。",
    replyTone: "答复时要同时说明谁负责清、是否追溯来源、是否需要进一步执法核查。",
    metrics: "渣土类问题数量、偷倒线索、清运时效、复发点位和来源追溯成功率",
    boundary: "把生活垃圾、建筑垃圾、装修垃圾和河道淤泥简单混成同一类问题"
  },
  {
    name: "暴露垃圾",
    typicalPlaces: "道路、绿地、广场、桶点周边、河岸步道和公共空间",
    coreCheck: "垃圾类型、裸露范围、周边桶点条件、保洁责任和是否存在抛撒来源",
    responsible: "责任区单位、环卫保洁单位、属地镇街或直接抛撒主体",
    basis: "绍兴市市容和环境卫生管理规定、绍兴市城镇生活垃圾分类管理办法",
    temporaryAction: "先清除裸露垃圾和污染，再核实责任主体和收运安排",
    evidence: "垃圾种类、落点分布、周边设施、保洁时段和整改前后照片",
    risks: "环境观感差、异味滋生、病媒风险和群众反感",
    primaryUnits: "环卫保洁、属地镇街、责任区单位",
    supportUnits: "社区物业、市场监管、综合执法等相关单位",
    longTerm: "要结合桶点优化、保洁频次和源头提醒，减少“刚清完又有”的反复现象。",
    replyTone: "答复宜先告诉群众已安排清理，再说明后续保洁和责任跟进。",
    metrics: "桶外暴露率、重点时段、责任区分布、复发率和清理时效",
    boundary: "把偶发抛撒和长期保洁缺位完全等同"
  },
  {
    name: "垃圾满溢",
    typicalPlaces: "垃圾分类投放点、果壳箱、临时收集点和商圈公共桶点",
    coreCheck: "满溢时段、桶点容量、清运频次、垃圾类型和责任人履职情况",
    responsible: "桶点管理单位、环卫清运单位、责任区单位或属地镇街",
    basis: "绍兴市城镇生活垃圾分类管理办法、绍兴市市容和环境卫生管理规定",
    temporaryAction: "先调度清运和保洁消杀，再评估桶点配置和清运计划",
    evidence: "桶点编号、满溢程度、清运台账、周边垃圾分布和现场照片",
    risks: "污水外溢、异味扰民、病媒滋生和投诉集中",
    primaryUnits: "环卫清运、属地镇街、责任区单位",
    supportUnits: "社区物业、商场园区运营单位、综合执法等相关单位",
    longTerm: "对高峰时段和重点桶点要动态调整容量、投放时间和清运频次。",
    replyTone: "答复时不能只说“已通知清运”，还要说明后续是否会优化桶点安排。",
    metrics: "满溢点位数、清运到场时长、重复满溢率、桶点容量匹配度和群众评价",
    boundary: "把桶满和周边乱投放都简单归因为“清运慢”"
  },
  {
    name: "绿地脏乱",
    typicalPlaces: "公园绿地、绿化带、街头花坛、草坪和景观节点",
    coreCheck: "垃圾杂物来源、养护边界、保洁责任、设施完好情况和游人行为因素",
    responsible: "绿化养护单位、环卫保洁单位、场所管理单位或属地镇街",
    basis: "绍兴市市容和环境卫生管理规定、浙江省城市市容和环境卫生管理条例",
    temporaryAction: "先清杂保洁、恢复观感，必要时同步修补设施和警示提醒",
    evidence: "绿地区域位置、垃圾类型、设施状况、养护痕迹和前后对比照片",
    risks: "观感下降、投诉反复、养护与保洁责任争议",
    primaryUnits: "绿化养护、环卫保洁、属地镇街",
    supportUnits: "公园管理、社区物业、综合执法等相关单位",
    longTerm: "宜把游客高频活动区和易藏污纳垢角落纳入加密保洁与养护联查。",
    replyTone: "答复时要把保洁、养护、设施维护的责任边界讲清楚。",
    metrics: "绿地脏乱点位、养护整改时效、复发率、节假日高发时段和满意度",
    boundary: "把绿化养护问题、游客抛撒问题和设施破损问题混成一个处理结论"
  },
  {
    name: "河道垃圾",
    typicalPlaces: "河岸、湖岸、桥下、水边步道和河道管理范围内",
    coreCheck: "垃圾位于岸线还是水面、是否涉嫌倾倒、是否影响行洪和来源线索",
    responsible: "水域管理单位、河道保洁单位、属地镇街或直接倾倒主体",
    basis: "浙江省河道管理条例、绍兴市市容和环境卫生管理规定",
    temporaryAction: "先组织清理和拦截，防止继续入河或影响行洪，再追查来源",
    evidence: "岸线或水边位置、垃圾类型、数量、来源痕迹和清理记录",
    risks: "水环境恶化、行洪受阻、反复偷倒和舆情放大",
    primaryUnits: "水域管理、属地镇街、综合执法",
    supportUnits: "水利、生态环境、环卫保洁、公安等相关单位",
    longTerm: "要把入河通道、桥下和易偷倒岸段纳入重点巡查与联防联控。",
    replyTone: "答复时要说明是岸线垃圾还是涉河倾倒，以及谁负责清理和复查。",
    metrics: "岸线垃圾数量、重点岸段、清理时效、来源追溯率和复发率",
    boundary: "把水面漂浮物、岸线垃圾和排口污染直接写成同一类问题"
  },
  {
    name: "河道漂浮物",
    typicalPlaces: "河道水面、湖面、桥区水域和入河口附近",
    coreCheck: "漂浮物类型、聚集位置、天气水情、来源方向和是否伴随异常污染",
    responsible: "水域保洁单位、水域管理单位或属地镇街",
    basis: "浙江省河道管理条例、绍兴市市容和环境卫生管理规定",
    temporaryAction: "先打捞拦截，必要时加密巡河并排查上游来源",
    evidence: "水面漂浮范围、类型、风向水流情况、打捞记录和现场图片",
    risks: "水面观感差、汛期聚集、排口异常和重复漂移回流",
    primaryUnits: "水域保洁、水利、属地镇街",
    supportUnits: "生态环境、综合执法、环卫保洁等相关单位",
    longTerm: "宜结合汛期、风向和上游口门特点，安排重点时段的拦截和复盘。",
    replyTone: "答复要说明是否已安排打捞、是否同步排查上游来源。",
    metrics: "漂浮物密度、重点水面、打捞频次、回流率和上游来源类型",
    boundary: "把水草、蓝藻、油污和普通生活漂浮物都按一个口径处理"
  },
  {
    name: "焚烧垃圾",
    typicalPlaces: "空闲地块、绿化带、河岸、道路边角和垃圾容器周边",
    coreCheck: "是否存在明火烟雾、焚烧物种类、责任人线索、影响范围和安全风险",
    responsible: "焚烧行为人、属地镇街、责任区单位或场所管理单位",
    basis: "浙江省城市市容和环境卫生管理条例、相关大气污染防治要求",
    temporaryAction: "先制止焚烧、灭除明火、清理余火并排查周边安全风险",
    evidence: "明火烟雾照片、焚烧物残留、时间地点、责任人线索和处置记录",
    risks: "火灾隐患、烟尘扰民、空气污染和舆情扩散",
    primaryUnits: "属地镇街、综合执法、环卫保洁",
    supportUnits: "消防、公安、生态环境、社区物业等相关单位",
    longTerm: "要在高发地块、落叶清扫时段和节日节点强化禁烧提醒与夜巡。",
    replyTone: "答复时先讲已采取的灭火和制止措施，再说明后续调查责任。",
    metrics: "焚烧点位数、处置到场时长、明火风险等级、复发率和夜间高发时段",
    boundary: "把清扫枝叶、露天烧烤和垃圾焚烧都不加区分地归成同一种行为"
  },
  {
    name: "沿街晾挂",
    typicalPlaces: "护栏、电杆、树木、桥栏、路牌和公共设施周边",
    coreCheck: "吊挂物种类、位置、持续时间、是否影响观感安全和责任主体",
    responsible: "吊挂行为人、责任区单位、属地镇街或场所管理单位",
    basis: "浙江省城市市容和环境卫生管理条例、绍兴市市容和环境卫生管理规定",
    temporaryAction: "先劝导收回、恢复市容，必要时同步消除安全隐患",
    evidence: "吊挂位置、设施类型、影响范围、责任线索和整改前后照片",
    risks: "影响市容、遮挡视线、缠挂设施和反复回潮",
    primaryUnits: "属地镇街、综合执法、责任区单位",
    supportUnits: "社区物业、公安、园林设施管理等相关单位",
    longTerm: "对反复发生的路段要加密巡查并做好责任区提醒和宣传引导。",
    replyTone: "答复时宜突出这是恢复市容秩序的问题，不必堆砌生硬条文。",
    metrics: "高发路段、即时整改率、重复点位、责任主体类型和群众回访结果",
    boundary: "把临时晾挂、广告横幅和商业展示物不加区分地混为一个结论"
  },
  {
    name: "占道撑伞",
    typicalPlaces: "人行道、店铺门前、广场周边、便民点外围和临时摊点集中区",
    coreCheck: "是否伴随经营、占道范围、通行影响、消防通道和是否超许可范围",
    responsible: "经营者、摊点责任人、属地镇街或场地管理单位",
    basis: "绍兴市市容和环境卫生管理规定、浙江省城市市容和环境卫生管理条例",
    temporaryAction: "先劝离、收拢伞棚和物品，保障人行通道畅通",
    evidence: "伞棚位置、占道宽度、经营行为、现场人流和整改前后照片",
    risks: "阻碍通行、店外经营扩张、消防通道受阻和高峰拥堵",
    primaryUnits: "属地镇街、综合执法、责任区单位",
    supportUnits: "公安、消防、市场监管、社区物业等相关单位",
    longTerm: "要结合商圈高峰、学校周边时段和便民点边界做常态提醒与复查。",
    replyTone: "答复要讲清是否占道、是否影响通行，以及后续是否持续巡查。",
    metrics: "占道点位数、当场整改率、高峰时段、重复经营主体和复发率",
    boundary: "只盯着“有伞”而不判断是否占道经营或影响通行"
  },
  {
    name: "露天烧烤",
    typicalPlaces: "夜市周边、道路沿线、店铺门前、居民区附近和公共场地",
    coreCheck: "是否露天作业、是否占道、是否产生油烟扰民、燃气明火风险和经营主体",
    responsible: "经营者、场地管理单位、属地镇街或责任区单位",
    basis: "绍兴市市容和环境卫生管理规定、相关大气污染防治要求、餐饮油烟治理口径",
    temporaryAction: "先制止露天作业、控制明火和油烟外溢，必要时先清场排险",
    evidence: "炉具位置、占道范围、油烟外溢、噪声垃圾、燃气使用和整改前后照片",
    risks: "油烟扰民、火灾隐患、占道经营、噪声投诉和夜间舆情",
    primaryUnits: "属地镇街、综合执法、市场监管",
    supportUnits: "消防、公安、生态环境、社区物业等相关单位",
    longTerm: "对夜宵集中区要同步做经营规范、油烟治理、消防检查和复盘联动。",
    replyTone: "答复时不能只讲“烧烤”，要把占道、油烟、噪声和安全一并交代。",
    metrics: "夜间高发点位、露天作业比例、油烟投诉量、整改回潮率和联动处置时长",
    boundary: "把店内合规烧烤、露天烧烤和居民自发明火活动简单归成同类"
  },
  {
    name: "违规户外广告",
    typicalPlaces: "沿街建筑立面、楼顶、灯杆、桥体、围挡和公共空间设施",
    coreCheck: "设施类型、设置主体、审批或同意情况、维护状态、结构安全和城市容貌影响",
    responsible: "广告设置者、招牌设置者、物业或属地镇街及主管单位",
    basis: "浙江省城市市容和环境卫生管理条例、城市容貌标准、城市户外广告和招牌设施技术标准",
    temporaryAction: "先消除坠落等安全风险，督促修复、整改或拆除",
    evidence: "设施全景、细部破损、设置位置、审批线索、结构风险和整改前后照片",
    risks: "高空坠落、光污染、视觉杂乱、遮挡通行视线和责任争议",
    primaryUnits: "综合执法、属地镇街、责任区单位",
    supportUnits: "住建、市政、公安、市场监管等相关单位",
    longTerm: "要把重点路段和大型设施纳入安全巡检、到期提醒和样式规范管理。",
    replyTone: "答复时先说明是否存在安全或审批问题，再讲整改路径。",
    metrics: "广告设施数量、破损率、违规率、整改时效和重点路段分布",
    boundary: "把户外广告、户外招牌、公益宣传和临时导视牌不加区分地处理"
  },
  {
    name: "人群聚集",
    typicalPlaces: "广场、公园、商圈、活动现场、学校周边和交通节点",
    coreCheck: "聚集原因、人数密度、通道状况、活动属性、承办责任和公共安全风险",
    responsible: "活动承办者、场所管理者、属地镇街或公安等主管单位",
    basis: "大型群众性活动安全管理条例、属地公共安全联动要求",
    temporaryAction: "先疏导控流、划定通道、提示风险，必要时启动现场应急联动",
    evidence: "聚集区域、人流密度、通道情况、活动信息、现场管控和处置记录",
    risks: "踩踏拥堵、交通阻断、治安风险和舆情扩散",
    primaryUnits: "公安、属地镇街、场所管理单位",
    supportUnits: "应急、消防、交通、综合执法、卫健等相关单位",
    longTerm: "对节庆、促销、演出等高发活动要提前评估容量、预案和引导机制。",
    replyTone: "答复时要把安全处置放在前面，避免把人群聚集简单当成一般市容问题。",
    metrics: "聚集事件数、预警触发率、疏导时长、重点时段和活动类型分布",
    boundary: "把普通等候、经许可活动和异常聚集直接写成同一种风险结论"
  },
  {
    name: "道路积水",
    typicalPlaces: "下穿通道、低洼路段、桥洞、施工道路和排水薄弱点",
    coreCheck: "积水深度、范围、持续时间、排水口状态、通行风险和天气水情",
    responsible: "排水市政单位、属地镇街、道路管理单位或施工责任单位",
    basis: "城市排水防涝应急管理要求、属地防汛排涝工作机制",
    temporaryAction: "先设置警示、疏导交通、排险抽排并排查井盖和排口",
    evidence: "积水深度范围、排口状态、天气时间、交通影响和处置前后照片视频",
    risks: "车辆熄火、行人跌倒、井盖险情、内涝扩散和舆情升级",
    primaryUnits: "市政排水、属地镇街、交警",
    supportUnits: "应急、消防、综合执法、施工单位等相关单位",
    longTerm: "要把易积水点位纳入汛前排查、雨中巡查和雨后复盘，形成工程与管养双闭环。",
    replyTone: "答复时要先说明是否已做警示和疏导，再说明排水和原因排查安排。",
    metrics: "易积水点位、积水持续时长、抽排到场时效、雨情关联度和复发率",
    boundary: "把短时雨后积水、长期排水薄弱和施工造成积水都写成同一原因"
  },
  {
    name: "打包垃圾",
    typicalPlaces: "商户门前、桶点周边、绿化带边、墙角和临时经营集中区",
    coreCheck: "垃圾是否袋装打包、投放时间、投放位置、责任主体和收运条件",
    responsible: "商户、居民、责任区单位、物业或属地镇街",
    basis: "绍兴市城镇生活垃圾分类管理办法、绍兴市市容和环境卫生管理规定",
    temporaryAction: "先组织规范归桶或清运，再提醒责任主体按时定点投放",
    evidence: "打包垃圾位置、袋数体量、周边桶点、责任主体线索和整改照片",
    risks: "桶外堆放、异味外溢、环境观感差和重复投诉",
    primaryUnits: "环卫保洁、属地镇街、责任区单位",
    supportUnits: "社区物业、市场监管、综合执法等相关单位",
    longTerm: "要结合商户投放高峰和桶点能力做定时提醒、清运优化和责任追踪。",
    replyTone: "答复时要让群众听明白：袋装不等于规范投放，关键看是否按点按时入桶。",
    metrics: "桶外打包垃圾数量、重点商户片区、投放高峰、复发率和整改时效",
    boundary: "把已分类暂存、临时收运过渡和违规桶外堆放不加区别地认定"
  },
  {
    name: "空闲地块",
    typicalPlaces: "待建地块、拆后空地、围挡内地块和城乡接合部空地",
    coreCheck: "地块边界、权属或管理主体、裸土杂草垃圾情况、围挡保洁和是否反复回潮",
    responsible: "权属单位、使用管理单位、属地镇街或代管单位",
    basis: "柯桥区空闲地块城市风貌提升治理口径、闲置土地处置办法相关边界说明",
    temporaryAction: "先做清杂、覆盖、围挡整治和安全警示，避免继续脏乱扩散",
    evidence: "地块边界、围挡状态、裸土杂草垃圾分布、权属线索和整改前后照片",
    risks: "风貌下降、偷倒滋生、扬尘问题和权属责任争议",
    primaryUnits: "属地镇街、综合执法、权属或代管单位",
    supportUnits: "自然资源、住建、环卫保洁、社区等相关单位",
    longTerm: "要把空闲地块纳入常态巡查、权属提醒和临时利用台账，避免反复脏乱差。",
    replyTone: "答复时要说明城管侧关注的是环境秩序和风貌，不直接替代闲置土地法定认定。",
    metrics: "空闲地块数量、裸土覆盖率、清杂时效、偷倒复发率和围挡完好率",
    boundary: "把城市风貌治理中的空闲地块问题直接等同于自然资源意义上的闲置土地认定"
  },
  {
    name: "餐饮油烟",
    typicalPlaces: "沿街餐饮店、商业综合体餐饮区、夜宵街和居民楼下餐饮单位",
    coreCheck: "经营主体、排烟方式、净化设施运行维护、异味投诉时段和是否反复投诉",
    responsible: "餐饮经营者、物业或园区管理单位、属地镇街及相关主管单位",
    basis: "饮食业油烟排放标准（GB 18483-2001）、柯桥区餐饮油烟治理业务口径、绍兴市餐厨垃圾管理办法相关协同要求",
    temporaryAction: "先核查净化设施运行和排烟状态，必要时要求立即停用异常设备并整改",
    evidence: "营业时段、排口位置、净化设施运行维护记录、现场气味感受和投诉时间规律",
    risks: "异味扰民、反复投诉、设备空转失效和邻里矛盾升级",
    primaryUnits: "属地镇街、生态环境、综合执法、市场监管",
    supportUnits: "社区物业、商务园区管理单位、消防等相关单位",
    longTerm: "宜围绕一户一档、清洗维护、重点商圈复查和数字监管做持续跟踪。",
    replyTone: "答复时不要讲技术黑话，重点说明会核查设备运行、维护记录和是否达标整改。",
    metrics: "油烟投诉量、重复投诉商户、净化设施清洗频次、整改完成率和夜间高发时段",
    boundary: "把居民家庭做饭油烟、店外烧烤烟雾和餐饮单位排放问题简单混为一谈"
  }
];

const focuses = [
  { title: "位置补全", mode: "collect", need: (s) => `具体位置、边界、楼栋门牌或桥梁路口等参照物，特别是${s.typicalPlaces}` },
  { title: "时间节点", mode: "collect", need: () => "发现时间、持续时间、是否集中在早晚高峰或夜间等关键时段" },
  { title: "数量规模", mode: "collect", need: () => "数量、面积、体量、覆盖范围或人流密度等规模信息" },
  { title: "影响对象", mode: "collect", need: () => "受影响的居民、商户、行人、车辆或公共设施对象" },
  { title: "影响范围", mode: "collect", need: () => "问题扩散范围、相邻点位和是否波及周边公共空间" },
  { title: "持续时长", mode: "collect", need: () => "问题是偶发、短时还是长期存在，以及最近是否反复出现" },
  { title: "历史线索", mode: "collect", need: () => "是否有同点位投诉、历史工单、巡查记录和已整改痕迹" },
  { title: "责任主体", mode: "judge", need: (s) => `${s.responsible}中谁应先行处置，谁负责后续恢复和复查` },
  { title: "分类边界", mode: "judge", need: (s) => `如何把${s.name}与相邻问题区分开，避免落入${s.boundary}` },
  { title: "误判排除", mode: "judge", need: (s) => `先排除不是${s.name}本类问题的情形，再判断是否需要转其他线条` },
  { title: "风险等级", mode: "judge", need: (s) => `是否已经触发${s.risks}，以及需要普通办理还是应急联动` },
  { title: "紧急触发", mode: "judge", need: (s) => `什么情况下要先执行${s.temporaryAction}而不是等待常规派单` },
  { title: "现场照片", mode: "evidence", need: (s) => `${s.evidence}中的核心照片和同角度前后对比材料` },
  { title: "视频截图", mode: "evidence", need: () => "视频截图、时间戳、方向信息和能证明位置关系的画面" },
  { title: "现场记录", mode: "evidence", need: () => "核查时间、到场人员、事实描述、处置动作和回访结果" },
  { title: "关键证据", mode: "evidence", need: (s) => `能说明${s.coreCheck}和责任归属的关键证据` },
  { title: "主办确定", mode: "dispatch", need: (s) => `${s.responsible}里谁最适合作为主办单位，保证工单可执行` },
  { title: "协办设置", mode: "dispatch", need: (s) => `围绕${s.supportUnits}设置哪些协办力量，避免相互退单` },
  { title: "派单时限", mode: "dispatch", need: () => "紧急、一般、反复三类情形分别如何设置办理时限" },
  { title: "地址写法", mode: "dispatch", need: () => "工单地址、参照点、范围描述和重点说明字段如何写清楚" },
  { title: "整改要求", mode: "rectify", need: (s) => `围绕${s.coreCheck}提出可检查、可复核、可量化的整改动作` },
  { title: "整改期限", mode: "rectify", need: () => "当场整改、限时整改和持续跟踪三类时限怎么区分" },
  { title: "临时处置", mode: "rectify", need: (s) => `先做${s.temporaryAction}，把现场风险压住` },
  { title: "复查标准", mode: "review", need: (s) => `现场是否恢复、证据是否完整以及${s.coreCheck}是否真正改善` },
  { title: "二次复核", mode: "review", need: () => "什么时候需要二次复查、雨后复查、夜间复查或高峰复查" },
  { title: "群众回访", mode: "review", need: () => "回访时问什么、怎么确认群众是否真正感受到改善" },
  { title: "超期说明", mode: "review", need: () => "超期原因、已做工作、下一步安排和新的完成时点" },
  { title: "夜间办理", mode: "time", need: () => "夜间值守、到场时效、照明取证和噪声扰民等特殊因素" },
  { title: "节假日办理", mode: "time", need: () => "节假日客流高峰、值班安排和节后复盘衔接" },
  { title: "恶劣天气", mode: "time", need: () => "暴雨、大风、高温等天气对核查和处置节奏的影响" },
  { title: "学校医院周边", mode: "scene", need: () => "上放学、探视高峰、救护通道和人流集中的特殊要求" },
  { title: "商圈集市周边", mode: "scene", need: () => "客流高峰、促销活动、夜间经营和周边环境承载能力" },
  { title: "小区物业协同", mode: "scene", need: () => "物业、业委会、社区与属地之间的信息和责任衔接" },
  { title: "工地施工关联", mode: "scene", need: () => "施工单位、运输链条、围挡边界和文明施工要求" },
  { title: "背街小巷边角地", mode: "scene", need: () => "监控盲区、责任交叉区和容易反复的边角部位管理" },
  { title: "安全隔离", mode: "risk", need: (s) => `${s.risks}中涉及人员通行、明火、坠落或井盖险情的防护动作` },
  { title: "跨镇街边界", mode: "coord", need: () => "边界不清时如何先处置现场、再协调属地责任" },
  { title: "跨部门联动", mode: "coord", need: (s) => `围绕${s.supportUnits}形成主办牵头、协办配合的闭环` },
  { title: "与公安协同", mode: "coord", need: () => "治安、交通、冲突风险或大型活动秩序问题何时要同步公安" },
  { title: "与消防协同", mode: "coord", need: () => "明火、燃气、堆料堵塞通道或高空坠落风险何时要同步消防" },
  { title: "与生态环境协同", mode: "coord", need: () => "油烟、异味、水体异常或污染排放问题何时要同步生态环境" },
  { title: "与市场监管协同", mode: "coord", need: () => "经营主体、食品经营、特种设备或商户规范问题何时要同步市场监管" },
  { title: "与住建水利市政协同", mode: "coord", need: () => "工程、排水、河道、设施维护和权属事项如何衔接住建水利市政" },
  { title: "舆情回应", mode: "comm", need: () => "网络传播、短视频曝光或集中投诉时怎样统一口径" },
  { title: "领导交办", mode: "comm", need: () => "如何快速梳理现状、原因、责任、措施、时限和风险" },
  { title: "法规引用", mode: "legal", need: (s) => `围绕${s.basis}讲清事实依据和处理边界，避免空泛贴法条` },
  { title: "柔性劝导", mode: "legal", need: () => "轻微情形如何先提醒、指导、限改并留痕" },
  { title: "处罚边界", mode: "legal", need: (s) => `什么情况下从提醒整改转入依法处置，特别是涉及${s.risks}或反复不改时` },
  { title: "企业服务", mode: "comm", need: () => "在不放松底线的前提下，怎么把整改要求讲得清楚又不生硬" },
  { title: "群众答复", mode: "comm", need: () => "如何用非技术语言说明核查结果、责任边界和下一步动作" },
  { title: "办结标准", mode: "review", need: () => "什么情况下能办结，什么情况下只能阶段反馈不能直接结案" },
  { title: "台账字段", mode: "data", need: () => "编号、地点、类型、责任主体、处置时限、整改结果、复查情况等字段设计" },
  { title: "统计口径", mode: "data", need: () => "同类问题如何统一口径，避免一件问题多头统计或漏统计" },
  { title: "看板指标", mode: "data", need: (s) => `结合${s.metrics}做趋势、分布、时效和复发分析` },
  { title: "高频反复", mode: "govern", need: () => "如何识别反复点位、反复主体和反复时段" },
  { title: "源头治理", mode: "govern", need: () => "从责任、设施、巡查、宣传和制度上减少反复发生" },
  { title: "宣传提醒", mode: "govern", need: () => "对居民、商户、施工单位或活动承办方做什么样的提醒最有效" },
  { title: "培训考核", mode: "govern", need: () => "一线受理、巡查、保洁和协办单位要掌握哪些统一标准" },
  { title: "争议协调", mode: "comm", need: () => "责任主体不认、属地有分歧或群众持续追问时怎样组织协调" },
  { title: "不属于职责", mode: "boundary", need: (s) => `先说明${s.boundary}这类边界问题，再给出正确转办方向和已做工作` }
];

const supplementarySources = [
  {
    title: "来源一：中华人民共和国国家标准城市容貌标准",
    url: "https://jst.zj.gov.cn/art/2023/10/17/art_1229749054_2493437.html",
    availability: "浙江省住房和城乡建设厅页面可获取标准核心条款和应用说明。",
    applies: "违规户外广告、沿街晾挂、市容环境、占道撑伞、绿地脏乱",
    notes: "可补充广告设施设置、安全维护、街面整洁和公共空间秩序的技术口径。"
  },
  {
    title: "来源二：浙江省住房和城乡建设厅关于规范户外广告和招牌设施设置管理的公开信息",
    url: "https://jst.zj.gov.cn/art/2024/10/25/art_1569971_58936878.html",
    availability: "官方新闻动态页面可获取正文。",
    applies: "违规户外广告、招牌整治、重点商圈风貌管理",
    notes: "可补充浙江省在安全巡检、规划引导、技术标准落地和城市精细化管理方面的做法。"
  },
  {
    title: "来源三：住房和城乡建设部关于发布行业标准《城市户外广告和招牌设施技术标准》的公告",
    url: "https://www.hunan.gov.cn/zqt/zcsd/202112/t20211228_21325333.html",
    availability: "省级政府转载住建部公告并附有标准获取说明。",
    applies: "违规户外广告、沿街招牌、照明亮化、安全巡检",
    notes: "可补充标准实施时间、适用范围和运行管理要求。"
  },
  {
    title: "来源四：大型群众性活动安全管理条例（国务院令第505号）",
    url: "https://www.gov.cn/flfg/2007-09/21/content_759965.htm",
    availability: "中国政府网可获取完整正文。",
    applies: "人群聚集、广场活动、商圈活动、活动安全许可和应急预案",
    notes: "可补充承办者责任、活动场所管理责任、安全方案和许可边界。"
  },
  {
    title: "来源五：住房和城乡建设部办公厅、应急管理部办公厅关于加强城市排水防涝应急管理工作的通知",
    url: "https://www.gov.cn/zhengce/zhengceku/202307/content_6889990.htm",
    availability: "中国政府网政策库可获取正文。",
    applies: "道路积水、城市内涝、下穿通道、低洼点和汛期联动处置",
    notes: "可补充责任人、排涝准备、应急响应、预警与部门协同。"
  },
  {
    title: "来源六：饮食业油烟排放标准（试行）",
    url: "https://www.mee.gov.cn/ywgz/fgbz/bz/bzwb/dqhjbh/dqgdwrywrwpfbz/200201/t20020101_67405.shtml",
    availability: "生态环境部页面可获取标准正文与实施信息。",
    applies: "餐饮油烟、露天烧烤衍生油烟问题、净化设施维护和排放控制",
    notes: "可补充适用范围、最高允许排放浓度、净化设施最低去除效率和不适用情形。"
  },
  {
    title: "来源七：绍兴市人民政府关于印发绍兴市城镇生活垃圾分类管理办法和绍兴市餐厨垃圾管理办法的通知",
    url: "https://www.sx.gov.cn/art/2020/10/14/art_1229265242_1639244.html",
    availability: "绍兴市政府官网页面可获取正文。",
    applies: "暴露垃圾、垃圾满溢、打包垃圾、餐饮油烟协同的餐厨垃圾管理",
    notes: "可补充责任区、分类投放、餐厨垃圾收运和责任追究口径。"
  },
  {
    title: "来源八：柯桥区综合行政执法局2025年上半年工作总结和下半年工作思路",
    url: "https://www.kq.gov.cn/art/2025/6/23/art_1229442810_4225211.html",
    availability: "柯桥区政府官网页面可获取正文。",
    applies: "空闲地块、餐饮油烟、市容环境、户外广告和智慧城管重点工作",
    notes: "可补充柯桥区本地治理重点、包点防治、一户一档和空闲地块风貌提升方向。"
  },
  {
    title: "来源九：闲置土地处置办法",
    url: "https://www.gov.cn/gongbao/content/2012/content_2201974.htm",
    availability: "中国政府网公报可获取规章正文。",
    applies: "空闲地块、权属边界、城市风貌治理与自然资源认定区分",
    notes: "可补充城市管理侧环境秩序治理与法定闲置土地认定之间的边界说明。"
  }
];

const volumeSpecs = [
  {
    fileName: "keqiao-city-management-qa-extension-2026-vol4.md",
    title: "柯桥区市容环境与城管事项扩展问答库（2026）第4卷",
    documentNumber: "柯桥事项扩展问答库第4卷（2026）",
    summary: "覆盖市容环境、乱堆物堆料、积存垃圾渣土3类事项，每类新增300组问答。",
    scenes: ["市容环境", "乱堆物堆料", "积存垃圾渣土"]
  },
  {
    fileName: "keqiao-city-management-qa-extension-2026-vol5.md",
    title: "柯桥区市容环境与城管事项扩展问答库（2026）第5卷",
    documentNumber: "柯桥事项扩展问答库第5卷（2026）",
    summary: "覆盖暴露垃圾、垃圾满溢、绿地脏乱3类事项，每类新增300组问答。",
    scenes: ["暴露垃圾", "垃圾满溢", "绿地脏乱"]
  },
  {
    fileName: "keqiao-city-management-qa-extension-2026-vol6.md",
    title: "柯桥区市容环境与城管事项扩展问答库（2026）第6卷",
    documentNumber: "柯桥事项扩展问答库第6卷（2026）",
    summary: "覆盖河道垃圾、河道漂浮物、焚烧垃圾3类事项，每类新增300组问答。",
    scenes: ["河道垃圾", "河道漂浮物", "焚烧垃圾"]
  },
  {
    fileName: "keqiao-city-management-qa-extension-2026-vol7.md",
    title: "柯桥区市容环境与城管事项扩展问答库（2026）第7卷",
    documentNumber: "柯桥事项扩展问答库第7卷（2026）",
    summary: "覆盖沿街晾挂、占道撑伞、露天烧烤3类事项，每类新增300组问答。",
    scenes: ["沿街晾挂", "占道撑伞", "露天烧烤"]
  },
  {
    fileName: "keqiao-city-management-qa-extension-2026-vol8.md",
    title: "柯桥区市容环境与城管事项扩展问答库（2026）第8卷",
    documentNumber: "柯桥事项扩展问答库第8卷（2026）",
    summary: "覆盖违规户外广告、人群聚集、道路积水3类事项，每类新增300组问答。",
    scenes: ["违规户外广告", "人群聚集", "道路积水"]
  },
  {
    fileName: "keqiao-city-management-qa-extension-2026-vol9.md",
    title: "柯桥区市容环境与城管事项扩展问答库（2026）第9卷",
    documentNumber: "柯桥事项扩展问答库第9卷（2026）",
    summary: "覆盖打包垃圾、空闲地块、餐饮油烟3类事项，每类新增300组问答。",
    scenes: ["打包垃圾", "空闲地块", "餐饮油烟"]
  }
];

const supplementSpec = {
  fileName: "keqiao-city-management-public-source-supplement-2026-vol2.md",
  title: "柯桥区市容环境与城管事项公开资料补充索引（2026）第2卷",
  documentNumber: "柯桥公开资料补充索引第2卷（2026）",
  summary: "补充户外广告、群体活动、道路积水、餐饮油烟、空闲地块等事项的公开资料来源和适用说明。"
};

function buildQuestion(scene, focus, role) {
  const mapping = {
    user: `问：群众咨询${scene.name}时，${focus.title}应该怎么理解和表达？`,
    accept: `问：受理${scene.name}线索时，${focus.title}至少要补到什么程度？`,
    inspect: `问：现场核实${scene.name}时，${focus.title}优先看什么？`,
    handle: `问：推进${scene.name}处置时，${focus.title}要怎样写进工单和要求里？`,
    review: `问：复盘${scene.name}时，${focus.title}怎样沉淀成长效做法？`
  };
  return mapping[role.key];
}

function buildAnswer(scene, focus, role) {
  const need = focus.need(scene);
  const commonTail = role.hint;
  switch (focus.mode) {
    case "collect":
      return `${scene.name}的${focus.title}不能只留笼统描述，要围绕${need}补齐信息。先核实${scene.coreCheck}，位置尽量落到${scene.typicalPlaces}这类可识别单元，并同步留存${scene.evidence}。${commonTail} ${scene.replyTone}`;
    case "judge":
      return `处理${scene.name}时，${focus.title}的关键是先把事实和边界看清。应结合${scene.basis}以及现场情况，重点判断${need}，避免落入“${scene.boundary}”这类误判。${commonTail} 主办侧要把判断依据写清楚，便于后续复查和解释。`;
    case "evidence":
      return `${scene.name}的${focus.title}主要服务于还原现场和固定责任。建议至少保留${need}，并让材料能够支撑${scene.coreCheck}的判断；涉及${scene.risks}时，证据还要覆盖时间、位置和责任主体线索。${commonTail} 归档时最好保留前后对比。`;
    case "dispatch":
      return `推进${scene.name}时，${focus.title}要让工单真正可执行。一般以${scene.responsible}为主，必要时联动${scene.primaryUnits}和${scene.supportUnits}，工单中要把${need}和${scene.coreCheck}写实，避免只写“已转相关单位”。${commonTail}`;
    case "rectify":
      return `${scene.name}进入整改阶段后，${focus.title}不能停留在口头提醒。要围绕${need}提出可检查、可复核的动作，先做${scene.temporaryAction}，再明确责任主体、完成时点和复查方式。${commonTail} ${scene.replyTone}`;
    case "review":
      return `复盘${scene.name}时，${focus.title}要看结果是不是真的落地。除了现场恢复情况，还要核对${need}是否完整，并判断${scene.risks}有没有真正下降。${commonTail} ${scene.longTerm}`;
    case "time":
      return `遇到${focus.title}场景时，${scene.name}处置要先稳住现场，再按职责闭环。重点看${need}，必要时由${scene.primaryUnits}先到场压风险，再和${scene.supportUnits}同步衔接。${commonTail} 回复群众时要说明已采取的临时措施和下一步安排。`;
    case "scene":
      return `在${focus.title}这类具体场景下处理${scene.name}，要把场所特点一起纳入研判。既要核实${scene.coreCheck}，也要关注${need}，责任上通常由${scene.responsible}牵头，必要时联动${scene.primaryUnits}。${commonTail} ${scene.replyTone}`;
    case "risk":
      return `涉及${focus.title}时，${scene.name}就不只是一般环境问题。先判断是否已经出现${scene.risks}，再根据${need}安排警示、隔离、疏导或停用措施，并同步固定${scene.evidence}。${commonTail} 有即时危险时先排险再补手续。`;
    case "coord":
      return `处理${scene.name}时，${focus.title}的重点是先把主诉求和主办单位定住。通常由${scene.responsible}牵头，围绕${need}与${scene.supportUnits}协同，边界不清时也要先做现场处置，避免互相退单。${commonTail}`;
    case "comm":
      return `围绕${focus.title}回复${scene.name}时，表达要让群众和协办单位都听得懂。先说明已核实的事实，再讲${need}和下一步安排，不轻易作超出职责的承诺；需要联动时要直接说明会同步${scene.supportUnits}。${commonTail}`;
    case "legal":
      return `在${scene.name}问题上谈${focus.title}，应先把事实讲清，再引用${scene.basis}。轻微情形可以先指导、劝导、限改；涉及${scene.risks}、反复不改或影响明显时，再依法推进后续处置。${commonTail}`;
    case "data":
      return `把${focus.title}用到${scene.name}管理里，核心是让数据能服务处置。建议围绕${need}设计字段或指标，并结合${scene.metrics}做趋势分析，这样才能判断问题是偶发还是反复。${commonTail} 统计口径统一后，知识召回也会更稳。`;
    case "govern":
      return `从${focus.title}看${scene.name}，关键不在一次办结，而在减少复发。应围绕${need}和${scene.longTerm}去设计常态机制，把巡查、宣传、设施配置和责任追踪串起来。${commonTail}`;
    case "boundary":
      return `遇到${focus.title}场景时，处理${scene.name}要把职责讲清楚。可以先协助固定现场、明确转办方向，但不要把“${scene.boundary}”直接写成已认定结论；答复中要说明当前已做工作和衔接单位。${commonTail}`;
    default:
      return `${scene.name}处理时要围绕${need}展开，兼顾${scene.coreCheck}和责任边界。${commonTail}`;
  }
}

function pad(num) {
  return String(num).padStart(3, "0");
}

function buildSceneSection(scene) {
  const lines = [`## ${scene.name}`, ""];
  let counter = 1;
  for (const focus of focuses) {
    for (const role of roles) {
      lines.push(`### ${scene.name}扩展问答${pad(counter)}：${focus.title}-${role.label}`);
      lines.push(buildQuestion(scene, focus, role));
      lines.push("");
      lines.push(`答：${buildAnswer(scene, focus, role)}`);
      lines.push("");
      counter += 1;
    }
  }
  return lines.join("\n");
}

function buildVolumeDoc(spec) {
  const targetScenes = spec.scenes.map((name) => scenes.find((scene) => scene.name === name));
  const header = [
    `# ${spec.title}`,
    "",
    "文件信息",
    "- 发布机关：绍兴市柯桥区综合行政执法局业务资料整理",
    "- 文件性质：面向系统召回的事项扩展问答语料",
    "- 生效日期：2026年5月2日",
    `- 覆盖事项：${spec.scenes.join("、")}`,
    "- 问答规模：每个事项新增300组问答，本卷共900组问答。",
    "- 使用说明：本卷在既有问答库和前序扩展卷基础上，进一步补充群众咨询、坐席受理、巡查核实、处置推进、复盘长效五个视角的语料。",
    ""
  ];
  const sections = targetScenes.map((scene) => buildSceneSection(scene));
  return `${header.join("\n")}\n${sections.join("\n\n")}\n`;
}

function buildSupplementDoc() {
  const lines = [
    `# ${supplementSpec.title}`,
    "",
    "文件信息",
    "- 发布机关：绍兴市柯桥区综合行政执法局业务资料整理",
    "- 文件性质：公开资料来源索引和事项适用说明",
    "- 生效日期：2026年5月2日",
    "- 适用区域：绍兴市柯桥区",
    "- 说明：本卷继续补充可公开查询的原始来源。能在官方页面直接获取完整正文的，标注为“官方页面可获取正文”；需要通过标准系统或公告页跳转获取的，记录原文地址和适用说明。",
    ""
  ];
  for (const source of supplementarySources) {
    lines.push(`## ${source.title}`);
    lines.push(`- 原文地址：${source.url}`);
    lines.push(`- 获取情况：${source.availability}`);
    lines.push(`- 适用事项：${source.applies}`);
    lines.push(`- 补充口径：${source.notes}`);
    lines.push("");
  }
  return `${lines.join("\n")}\n`;
}

async function updateManifest() {
  const original = await readFile(manifestPath, "utf8");
  const existingLines = original.trimEnd().split("\n");
  const generatedFiles = new Set([supplementSpec.fileName, ...volumeSpecs.map((item) => item.fileName)]);
  const kept = existingLines.filter((line, index) => index === 0 || !generatedFiles.has(line.split("|")[0]));
  const additions = [
    [
      supplementSpec.fileName,
      supplementSpec.title,
      "business",
      "绍兴市柯桥区综合行政执法局业务资料整理",
      supplementSpec.documentNumber,
      "2026-05-02",
      supplementSpec.summary,
      "shaoxing-keqiao"
    ].join("|"),
    ...volumeSpecs.map((spec) =>
      [
        spec.fileName,
        spec.title,
        "business",
        "绍兴市柯桥区综合行政执法局业务资料整理",
        spec.documentNumber,
        "2026-05-02",
        spec.summary,
        "shaoxing-keqiao"
      ].join("|")
    )
  ];
  await writeFile(manifestPath, `${kept.join("\n")}\n${additions.join("\n")}\n`, "utf8");
}

async function generate() {
  await writeFile(path.join(importDir, supplementSpec.fileName), buildSupplementDoc(), "utf8");
  for (const spec of volumeSpecs) {
    await writeFile(path.join(importDir, spec.fileName), buildVolumeDoc(spec), "utf8");
  }
  await updateManifest();
}

async function requestJson(url, options = {}) {
  const response = await fetch(url, options);
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText} @ ${url}`);
  }
  return response.json();
}

async function uploadOne(baseUrl, meta) {
  const documents = await requestJson(`${baseUrl}/api/v1/knowledge/documents`);
  const activeSameTitle = (documents.data || []).filter((item) => item.title === meta.title && item.status !== "ABOLISHED");
  for (const doc of activeSameTitle) {
    await requestJson(`${baseUrl}/api/v1/knowledge/documents/${doc.id}/status`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ status: "ABOLISHED" })
    });
  }

  const content = await readFile(path.join(importDir, meta.fileName));
  const form = new FormData();
  form.append("title", meta.title);
  form.append("category", "BUSINESS");
  form.append("sourceOrg", "绍兴市柯桥区综合行政执法局业务资料整理");
  form.append("documentNumber", meta.documentNumber);
  form.append("effectiveFrom", "2026-05-02");
  form.append("regionCode", "shaoxing-keqiao");
  form.append("summary", meta.summary);
  form.append("file", new Blob([content], { type: "text/markdown" }), meta.fileName);

  const upload = await requestJson(`${baseUrl}/api/v1/knowledge/documents`, {
    method: "POST",
    body: form
  });
  const documentId = upload.data.id;
  await requestJson(`${baseUrl}/api/v1/knowledge/documents/${documentId}/index`, { method: "POST" });
  return documentId;
}

async function upload(baseUrl) {
  const metas = [
    {
      fileName: supplementSpec.fileName,
      title: supplementSpec.title,
      documentNumber: supplementSpec.documentNumber,
      summary: supplementSpec.summary
    },
    ...volumeSpecs.map((spec) => ({
      fileName: spec.fileName,
      title: spec.title,
      documentNumber: spec.documentNumber,
      summary: spec.summary
    }))
  ];
  const results = [];
  for (const meta of metas) {
    const id = await uploadOne(baseUrl, meta);
    results.push({ title: meta.title, id });
  }
  return results;
}

async function main() {
  const command = process.argv[2] ?? "generate";
  if (command === "generate") {
    await generate();
    console.log("generated");
    return;
  }
  if (command === "upload") {
    const baseUrl = process.argv[3] ?? "http://127.0.0.1:8081";
    const results = await upload(baseUrl);
    console.log(JSON.stringify(results, null, 2));
    return;
  }
  throw new Error(`unknown command: ${command}`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
