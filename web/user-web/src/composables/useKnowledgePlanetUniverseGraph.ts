import ForceGraph3D, { type ForceGraph3DInstance } from "3d-force-graph";
import * as THREE from "three";
import { onUnmounted, type ShallowRef } from "vue";
import type {
  KnowledgePlanetUniverse,
  KnowledgePlanetView,
} from "../api/knowledgePlanet";
import {
  addUniverseStarfield,
  createFresnelPlanetObject,
  createKnowledgeCrystalObject,
} from "./knowledgePlanetThree";

export type UniverseViewMode = "galaxy" | "planet" | "full";

export type ForceGraphNode = {
  id: string;
  kind: "planet" | "knowledge";
  title: string;
  summary: string;
  color: string;
  val: number;
  planet?: KnowledgePlanetView;
  planetId?: string | null;
};

type GraphLink = { source: string; target: string; kind: string };

/** 3d-force-graph 运行时节点带坐标，与业务字段合并 */
type FgNode = ForceGraphNode & { x?: number; y?: number; z?: number };

function fgNode(obj: object): FgNode {
  return obj as FgNode;
}

function fgLink(obj: object): GraphLink {
  return obj as GraphLink;
}

function rgbHex(rgb: number): string {
  return `#${(rgb & 0xffffff).toString(16).padStart(6, "0")}`;
}

function linkEndpointId(endpoint: string | { id?: string }): string {
  return typeof endpoint === "string" ? endpoint : String(endpoint.id ?? "");
}

/** 3D 连线流动粒子全局预算，避免节点/边过多时满屏高速光点 */
const MAX_FLOW_PARTICLES = 28;

type LinkFlowProfile = {
  particleCount: (kind: string, linkId: string) => number;
  particleSpeed: number;
  particleWidth: number;
  linkOpacity: number;
  linkColorAlpha: number;
};

function stableLinkSample(linkId: string, threshold: number): boolean {
  let hash = 0;
  for (let i = 0; i < linkId.length; i++) {
    hash = (hash * 31 + linkId.charCodeAt(i)) >>> 0;
  }
  return (hash % 1000) / 1000 < threshold;
}

function linkFlowKey(source: string, target: string): string {
  return source < target ? `${source}|${target}` : `${target}|${source}`;
}

function computeLinkFlowProfile(linkCount: number): LinkFlowProfile {
  if (linkCount <= 0) {
    return {
      particleCount: () => 0,
      particleSpeed: 0,
      particleWidth: 0,
      linkOpacity: 0.45,
      linkColorAlpha: 0.22,
    };
  }
  if (linkCount <= 15) {
    return {
      particleCount: (kind) => (kind === "relation" ? 2 : 1),
      particleSpeed: 0.004,
      particleWidth: 1.4,
      linkOpacity: 0.42,
      linkColorAlpha: 0.22,
    };
  }
  if (linkCount <= 35) {
    return {
      particleCount: (kind) => (kind === "relation" ? 1 : 1),
      particleSpeed: 0.003,
      particleWidth: 1.2,
      linkOpacity: 0.36,
      linkColorAlpha: 0.18,
    };
  }
  const sampleRate = Math.min(1, MAX_FLOW_PARTICLES / linkCount);
  return {
    particleCount: (kind, linkId) => {
      if (!stableLinkSample(linkId, sampleRate)) return 0;
      return kind === "relation" ? 1 : 0;
    },
    particleSpeed: 0.002,
    particleWidth: 0.9,
    linkOpacity: 0.28,
    linkColorAlpha: 0.14,
  };
}

function buildGraphPayload(
  universe: KnowledgePlanetUniverse,
  mode: UniverseViewMode,
  selectedPlanetId: string | null,
): { nodes: ForceGraphNode[]; links: GraphLink[] } {
  const planetNodes: ForceGraphNode[] = universe.planets.map((p) => ({
    id: p.id,
    kind: "planet" as const,
    title: p.name,
    summary: p.summary,
    color: rgbHex(p.colorRgb),
    val: Math.max(8, p.displaySize * 1.2),
    planet: p,
    planetId: null,
  }));

  const knowledgeNodes: ForceGraphNode[] = universe.nodes
    .filter((n) => n.kind === "knowledge")
    .map((n) => {
      const planet = universe.planets.find((p) => p.id === n.planetId);
      return {
        id: n.id,
        kind: "knowledge" as const,
        title: n.title,
        summary: n.summary,
        color: rgbHex(planet?.colorRgb ?? 0x4facfe),
        val: 4,
        planet,
        planetId: n.planetId,
      };
    });

  let nodes = [...planetNodes, ...knowledgeNodes];
  let links: GraphLink[] = universe.links.map((l) => ({
    source: l.sourceId,
    target: l.targetId,
    kind: l.kind,
  }));

  if (mode === "full") {
    // 全星图：保留全部星球、知识点与连线
  } else if (mode === "galaxy") {
    nodes = planetNodes;
    links = [];
  } else if (selectedPlanetId) {
    const allowed = new Set<string>([selectedPlanetId]);
    for (const n of knowledgeNodes) {
      if (n.planetId === selectedPlanetId) allowed.add(n.id);
    }
    nodes = nodes.filter((n) => allowed.has(n.id));
    links = links.filter(
      (l) => allowed.has(linkEndpointId(l.source)) && allowed.has(linkEndpointId(l.target)),
    );
  }

  return { nodes, links };
}

export function useKnowledgePlanetUniverseGraph(containerRef: ShallowRef<HTMLElement | null>) {
  let graph: ForceGraph3DInstance | null = null;
  let resizeObs: ResizeObserver | null = null;
  let onPlanetClick: ((planet: KnowledgePlanetView) => void) | null = null;

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
  }

  onUnmounted(dispose);

  function resizeGraph() {
    const el = containerRef.value;
    if (!graph || !el) return;
    const w = Math.max(el.clientWidth, 1);
    const h = Math.max(el.clientHeight, 1);
    graph.width(w).height(h);
  }

  function mount(
    universe: KnowledgePlanetUniverse,
    mode: UniverseViewMode,
    selectedPlanetId: string | null,
  ): boolean {
    const el = containerRef.value;
    if (!el) return false;
    dispose();

    const { nodes, links } = buildGraphPayload(universe, mode, selectedPlanetId);
    if (nodes.length === 0) return false;

    const flowProfile = computeLinkFlowProfile(links.length);

    const w = Math.max(el.clientWidth, 1);
    const h = Math.max(el.clientHeight, 1);

    graph = new ForceGraph3D(el)
      .width(w)
      .height(h)
      .graphData({ nodes, links })
      .backgroundColor("#030712")
      .showNavInfo(false)
      .nodeRelSize(1)
      .nodeVal((n) => fgNode(n).val)
      .nodeLabel((n) => {
        const node = fgNode(n);
        return node.kind === "planet"
          ? `${node.title}（${node.planet?.nodeCount ?? 0} 个知识点）`
          : node.title;
      })
      .nodeThreeObject((n) => {
        const node = fgNode(n);
        if (node.kind === "planet") {
          return createFresnelPlanetObject(node.color, node.planet?.displaySize ?? node.val);
        }
        return createKnowledgeCrystalObject(node.color);
      })
      .nodeThreeObjectExtend(false)
      .linkColor(() => `rgba(0, 242, 254, ${flowProfile.linkColorAlpha})`)
      .linkWidth((l) => (fgLink(l).kind === "relation" ? 0.9 : 0.55))
      .linkOpacity(flowProfile.linkOpacity)
      .linkDirectionalParticles((l) => {
        const link = fgLink(l);
        const linkId = linkFlowKey(linkEndpointId(link.source), linkEndpointId(link.target));
        return flowProfile.particleCount(link.kind, linkId);
      })
      .linkDirectionalParticleWidth(flowProfile.particleWidth)
      .linkDirectionalParticleSpeed(flowProfile.particleSpeed)
      .linkDirectionalParticleColor(() => "#00f2fe")
      .warmupTicks(120)
      .cooldownTicks(80)
      .onNodeClick((n) => {
        const node = fgNode(n);
        if (node.kind === "planet" && node.planet && onPlanetClick) {
          onPlanetClick(node.planet);
          return;
        }
        if (!graph || node.x == null) return;
        const dist = 55;
        const ratio = 1 + dist / Math.hypot(node.x, node.y ?? 0, node.z ?? 0);
        graph.cameraPosition(
          { x: (node.x ?? 0) * ratio, y: (node.y ?? 0) * ratio, z: (node.z ?? 0) * ratio },
          { x: node.x, y: node.y ?? 0, z: node.z ?? 0 },
          1600,
        );
      });

    const scene = graph.scene();
    addUniverseStarfield(scene);
    scene.add(new THREE.AmbientLight(0x334455, 0.9));
    const dir = new THREE.DirectionalLight(0xffffff, 0.8);
    dir.position.set(80, 120, 60);
    scene.add(dir);
    const rim = new THREE.PointLight(0x00f2fe, 0.7, 500);
    rim.position.set(-60, 40, 80);
    scene.add(rim);

    graph.cameraPosition({ z: mode === "galaxy" ? 260 : 110 });

    resizeObs = new ResizeObserver(() => resizeGraph());
    resizeObs.observe(el);

    return true;
  }

  function focusPlanet(planetId: string) {
    if (!graph) return;
    const coords = graph.graphData().nodes.find((n) => fgNode(n).id === planetId);
    if (!coords) return;
    const c = fgNode(coords);
    if (c.x == null) return;
    const dist = 70;
    const ratio = 1 + dist / Math.hypot(c.x, c.y ?? 0, c.z ?? 0);
    graph.cameraPosition(
      {
        x: (c.x ?? 0) * ratio,
        y: (c.y ?? 0) * ratio,
        z: (c.z ?? 0) * ratio,
      },
      { x: c.x, y: c.y ?? 0, z: c.z ?? 0 },
      2000,
    );
  }

  function setPlanetClickHandler(handler: (planet: KnowledgePlanetView) => void) {
    onPlanetClick = handler;
  }

  return { mount, dispose, resizeGraph, focusPlanet, setPlanetClickHandler };
}
