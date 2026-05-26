import ForceGraph from "force-graph";
import { onUnmounted, type ShallowRef } from "vue";
import type { KnowledgePlanetUniverse } from "../api/knowledgePlanet";

export type StarMapNodeKind = "planet" | "knowledge";

export type StarMapNode = {
  id: string;
  kind: StarMapNodeKind;
  title: string;
  summary: string;
  color: string;
  val: number;
  planetId: string | null;
  x?: number;
  y?: number;
};

type StarMapLink = {
  source: string | StarMapNode;
  target: string | StarMapNode;
  kind: string;
};

/** kapsule 工厂：ForceGraph()(domEl) */
type ForceGraphFactory = () => (dom: HTMLElement) => ForceGraphChain;
type ForceGraphChain = {
  width: (w: number) => ForceGraphChain;
  height: (h: number) => ForceGraphChain;
  graphData: {
    (d: { nodes: StarMapNode[]; links: StarMapLink[] }): ForceGraphChain;
    (): { nodes: StarMapNode[]; links: StarMapLink[] };
  };
  backgroundColor: (c: string) => ForceGraphChain;
  enableNodeDrag: (v: boolean) => ForceGraphChain;
  enableZoomInteraction: (v: boolean) => ForceGraphChain;
  enablePanInteraction: (v: boolean) => ForceGraphChain;
  nodeRelSize: (n: number) => ForceGraphChain;
  nodeVal: (fn: (n: StarMapNode) => number) => ForceGraphChain;
  linkColor: (fn: (l: StarMapLink) => string) => ForceGraphChain;
  linkWidth: (fn: (l: StarMapLink) => number) => ForceGraphChain;
  linkLineDash: (fn: (l: StarMapLink) => number[] | null) => ForceGraphChain;
  linkDirectionalParticles: (fn: (l: StarMapLink) => number) => ForceGraphChain;
  linkDirectionalParticleWidth: (n: number) => ForceGraphChain;
  linkDirectionalParticleSpeed: (n: number) => ForceGraphChain;
  linkDirectionalParticleColor: (fn: () => string) => ForceGraphChain;
  nodeCanvasObjectMode: (fn: () => string) => ForceGraphChain;
  nodeCanvasObject: (fn: (node: StarMapNode, ctx: CanvasRenderingContext2D) => void) => ForceGraphChain;
  onNodeHover: (fn: (node: StarMapNode | null) => void) => ForceGraphChain;
  onNodeClick: (fn: (node: StarMapNode) => void) => ForceGraphChain;
  warmupTicks: (n: number) => ForceGraphChain;
  cooldownTicks: (n: number) => ForceGraphChain;
  d3AlphaDecay: (n: number) => ForceGraphChain;
  d3VelocityDecay: (n: number) => ForceGraphChain;
  zoomToFit: (ms?: number, padding?: number) => void;
  centerAt: (x: number, y: number, ms?: number) => void;
  zoom: (level: number, ms?: number) => void;
  d3Force: (name: string) => {
    strength?: (fn: (n: StarMapNode) => number) => unknown;
    distance?: (fn: (l: StarMapLink) => number) => unknown;
  };
  _destructor?: () => void;
};

const createForceGraph = ForceGraph as unknown as ForceGraphFactory;

function rgbHex(rgb: number): string {
  return `#${(rgb & 0xffffff).toString(16).padStart(6, "0")}`;
}

function linkEndpointId(endpoint: string | StarMapNode): string {
  return typeof endpoint === "string" ? endpoint : endpoint.id;
}

function buildStarMapData(universe: KnowledgePlanetUniverse): {
  nodes: StarMapNode[];
  links: StarMapLink[];
} {
  const planetById = new Map(universe.planets.map((p) => [p.id, p]));

  const nodes: StarMapNode[] = [
    ...universe.planets.map((p) => ({
      id: p.id,
      kind: "planet" as const,
      title: p.name,
      summary: p.summary,
      color: rgbHex(p.colorRgb),
      val: Math.max(10, Math.min(22, p.displaySize * 0.85)),
      planetId: null,
    })),
    ...universe.nodes
      .filter((n) => n.kind === "knowledge")
      .map((n) => {
        const planet = n.planetId ? planetById.get(n.planetId) : undefined;
        return {
          id: n.id,
          kind: "knowledge" as const,
          title: n.title,
          summary: n.summary,
          color: rgbHex(planet?.colorRgb ?? 0x4facfe),
          val: 4,
          planetId: n.planetId,
        };
      }),
  ];

  const links: StarMapLink[] = universe.links.map((l) => ({
    source: l.sourceId,
    target: l.targetId,
    kind: l.kind,
  }));

  return { nodes, links };
}

function truncateLabel(text: string, maxLen: number): string {
  const t = text.trim();
  if (t.length <= maxLen) return t;
  return `${t.slice(0, maxLen - 1)}…`;
}

export function useKnowledgePlanetStarMap(containerRef: ShallowRef<HTMLElement | null>) {
  let graph: ForceGraphChain | null = null;
  let resizeObs: ResizeObserver | null = null;
  let hoverId: string | null = null;
  let highlightIds = new Set<string>();
  let focusPlanetId: string | null = null;
  let searchQuery = "";

  let onPlanetClick: ((planetId: string) => void) | null = null;
  let onKnowledgeClick: ((nodeId: string) => void) | null = null;

  function neighborIds(nodeId: string): Set<string> {
    const out = new Set<string>([nodeId]);
    if (!graph) return out;
    const { links } = graph.graphData();
    for (const l of links) {
      const s = linkEndpointId(l.source);
      const t = linkEndpointId(l.target);
      if (s === nodeId) out.add(t);
      if (t === nodeId) out.add(s);
    }
    return out;
  }

  function nodeAlpha(node: StarMapNode): number {
    if (searchQuery) {
      const q = searchQuery.toLowerCase();
      const hit =
        node.title.toLowerCase().includes(q) ||
        node.summary.toLowerCase().includes(q);
      if (!hit) return 0.12;
    }
    if (focusPlanetId) {
      const inCluster = node.id === focusPlanetId || node.planetId === focusPlanetId;
      if (!inCluster) return 0.15;
    }
    if (hoverId) {
      return highlightIds.has(node.id) ? 1 : 0.2;
    }
    return 1;
  }

  function drawPlanetNode(
    node: StarMapNode,
    ctx: CanvasRenderingContext2D,
    alpha: number,
  ) {
    const x = node.x ?? 0;
    const y = node.y ?? 0;
    const r = node.val;

    ctx.save();
    ctx.globalAlpha = alpha * 0.35;
    const g = ctx.createRadialGradient(x, y, r * 0.2, x, y, r * 2.2);
    g.addColorStop(0, node.color);
    g.addColorStop(1, "transparent");
    ctx.fillStyle = g;
    ctx.beginPath();
    ctx.arc(x, y, r * 2.2, 0, Math.PI * 2);
    ctx.fill();

    ctx.globalAlpha = alpha;
    ctx.strokeStyle = node.color;
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.arc(x, y, r, 0, Math.PI * 2);
    ctx.stroke();

    ctx.globalAlpha = alpha * 0.45;
    ctx.beginPath();
    ctx.arc(x, y, r * 1.35, 0, Math.PI * 2);
    ctx.stroke();

    const fontSize = Math.max(10, 11);
    ctx.font = `500 ${fontSize}px system-ui, sans-serif`;
    ctx.textAlign = "center";
    ctx.textBaseline = "top";
    ctx.fillStyle = `rgba(232, 244, 255, ${alpha})`;
    ctx.fillText(truncateLabel(node.title, 14), x, y + r + 4);
    ctx.restore();
  }

  function drawKnowledgeNode(
    node: StarMapNode,
    ctx: CanvasRenderingContext2D,
    alpha: number,
  ) {
    const x = node.x ?? 0;
    const y = node.y ?? 0;
    const r = node.val;

    ctx.save();
    ctx.globalAlpha = alpha * 0.5;
    ctx.fillStyle = node.color;
    ctx.beginPath();
    ctx.arc(x, y, r * 1.8, 0, Math.PI * 2);
    ctx.fill();

    ctx.globalAlpha = alpha;
    ctx.fillStyle = node.color;
    ctx.beginPath();
    ctx.arc(x, y, r, 0, Math.PI * 2);
    ctx.fill();

    const fontSize = 9;
    ctx.font = `400 ${fontSize}px system-ui, sans-serif`;
    ctx.textAlign = "center";
    ctx.textBaseline = "top";
    ctx.fillStyle = `rgba(224, 232, 240, ${alpha * 0.9})`;
    ctx.fillText(truncateLabel(node.title, 18), x, y + r + 3);
    ctx.restore();
  }

  function dispose() {
    resizeObs?.disconnect();
    resizeObs = null;
    if (graph) {
      graph._destructor?.();
      graph = null;
    }
    if (containerRef.value) {
      containerRef.value.innerHTML = "";
    }
    hoverId = null;
    highlightIds = new Set();
  }

  onUnmounted(dispose);

  function resizeGraph() {
    const el = containerRef.value;
    if (!graph || !el) return;
    graph.width(Math.max(el.clientWidth, 1));
    graph.height(Math.max(el.clientHeight, 1));
  }

  function refreshHighlight() {
    if (graph) graph.nodeRelSize(1);
  }

  function mount(universe: KnowledgePlanetUniverse): boolean {
    const el = containerRef.value;
    if (!el) return false;
    dispose();

    const { nodes, links } = buildStarMapData(universe);
    if (nodes.length === 0) return false;

    const w = Math.max(el.clientWidth, 1);
    const h = Math.max(el.clientHeight, 1);

    graph = createForceGraph()(el)
      .width(w)
      .height(h)
      .graphData({ nodes, links })
      .backgroundColor("rgba(3, 7, 18, 0)")
      .enableNodeDrag(true)
      .enableZoomInteraction(true)
      .enablePanInteraction(true)
      .nodeRelSize(1)
      .nodeVal((n: StarMapNode) => n.val)
      .linkColor((l: StarMapLink) => {
        const a = hoverId
          ? highlightIds.has(linkEndpointId(l.source)) &&
            highlightIds.has(linkEndpointId(l.target))
            ? 0.55
            : 0.08
          : 0.35;
        return l.kind === "orbit"
          ? `rgba(0, 242, 254, ${a * 0.5})`
          : `rgba(123, 156, 255, ${a})`;
      })
      .linkWidth((l: StarMapLink) => (l.kind === "relation" ? 1.2 : 0.7))
      .linkLineDash((l: StarMapLink) => (l.kind === "orbit" ? [4, 6] : null))
      .linkDirectionalParticles((l: StarMapLink) => (l.kind === "relation" && hoverId ? 2 : 0))
      .linkDirectionalParticleWidth(2)
      .linkDirectionalParticleSpeed(0.004)
      .linkDirectionalParticleColor(() => "#00f2fe")
      .nodeCanvasObjectMode(() => "replace")
      .nodeCanvasObject((node: StarMapNode, ctx: CanvasRenderingContext2D) => {
        const alpha = nodeAlpha(node);
        if (alpha < 0.05) return;
        if (node.kind === "planet") drawPlanetNode(node, ctx, alpha);
        else drawKnowledgeNode(node, ctx, alpha);
      })
      .onNodeHover((node: StarMapNode | null) => {
        hoverId = node?.id ?? null;
        highlightIds = node ? neighborIds(node.id) : new Set();
        refreshHighlight();
      })
      .onNodeClick((node: StarMapNode) => {
        if (node.kind === "planet") onPlanetClick?.(node.id);
        else onKnowledgeClick?.(node.id);
      })
      .warmupTicks(80)
      .cooldownTicks(60)
      .d3AlphaDecay(0.022)
      .d3VelocityDecay(0.35);

    graph.d3Force("charge")?.strength?.((n: StarMapNode) => (n.kind === "planet" ? -180 : -40));
    graph.d3Force("link")?.distance?.((l: StarMapLink) => (l.kind === "orbit" ? 42 : 72));

    resizeObs = new ResizeObserver(() => resizeGraph());
    resizeObs.observe(el);

    window.setTimeout(() => fitView(), 400);

    return true;
  }

  function fitView(durationMs = 500) {
    graph?.zoomToFit(durationMs, 48);
  }

  function setFocusPlanet(planetId: string | null) {
    focusPlanetId = planetId;
    refreshHighlight();
  }

  function setSearchQuery(q: string) {
    searchQuery = q.trim().toLowerCase();
    refreshHighlight();
  }

  function centerOnNode(nodeId: string, durationMs = 400) {
    if (!graph) return;
    const node = graph.graphData().nodes.find((n: StarMapNode) => n.id === nodeId);
    if (!node || node.x == null || node.y == null) return;
    graph.centerAt(node.x, node.y, durationMs);
    graph.zoom(2.2, durationMs);
  }

  function setPlanetClickHandler(handler: (planetId: string) => void) {
    onPlanetClick = handler;
  }

  function setKnowledgeClickHandler(handler: (nodeId: string) => void) {
    onKnowledgeClick = handler;
  }

  return {
    mount,
    dispose,
    resizeGraph,
    fitView,
    setFocusPlanet,
    setSearchQuery,
    centerOnNode,
    setPlanetClickHandler,
    setKnowledgeClickHandler,
  };
}
