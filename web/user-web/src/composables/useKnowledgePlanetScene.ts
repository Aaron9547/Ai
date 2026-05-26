import * as THREE from "three";
import { onUnmounted, type ShallowRef } from "vue";

export type PlanetSceneHandle = {
  dispose: () => void;
  setHoverBoost: (active: boolean) => void;
};

export function useKnowledgePlanetScene(
  containerRef: ShallowRef<HTMLElement | null>,
): { mount: () => PlanetSceneHandle | null } {
  let frameId = 0;
  let renderer: THREE.WebGLRenderer | null = null;
  let group: THREE.Group | null = null;
  let pointsMat: THREE.PointsMaterial | null = null;
  let spinY = 0.003;
  let spinX = 0.001;
  let startMs = 0;

  function dispose() {
    if (frameId) cancelAnimationFrame(frameId);
    frameId = 0;
    if (renderer) {
      renderer.dispose();
      renderer.domElement.remove();
      renderer = null;
    }
    group = null;
    pointsMat = null;
  }

  onUnmounted(dispose);

  function mount(): PlanetSceneHandle | null {
    const el = containerRef.value;
    if (!el) return null;
    dispose();

    const w = Math.max(el.clientWidth, 1);
    const h = Math.max(el.clientHeight, 1);
    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(45, w / h, 0.1, 1000);
    camera.position.set(0, 0.1, 6.2);

    renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
    renderer.setSize(w, h);
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    el.appendChild(renderer.domElement);

    group = new THREE.Group();
    scene.add(group);

    const ico = new THREE.IcosahedronGeometry(2, 4);
    const posAttr = ico.attributes.position;
    const cloudPos = new Float32Array(800 * 3);
    for (let i = 0; i < 800; i++) {
      const v = new THREE.Vector3(
        (Math.random() - 0.5) * 5.5,
        (Math.random() - 0.5) * 5.5,
        (Math.random() - 0.5) * 5.5,
      );
      if (v.length() > 2.8) v.setLength(2.2 + Math.random() * 0.8);
      cloudPos[i * 3] = v.x;
      cloudPos[i * 3 + 1] = v.y;
      cloudPos[i * 3 + 2] = v.z;
    }
    const cloudGeom = new THREE.BufferGeometry();
    cloudGeom.setAttribute("position", new THREE.BufferAttribute(cloudPos, 3));
    pointsMat = new THREE.PointsMaterial({
      color: 0x6366f1,
      size: 0.035,
      transparent: true,
      opacity: 0.65,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
      sizeAttenuation: true,
    });
    group.add(new THREE.Points(cloudGeom, pointsMat));

    const verts: THREE.Vector3[] = [];
    for (let i = 0; i < posAttr.count; i++) {
      verts.push(new THREE.Vector3(posAttr.getX(i), posAttr.getY(i), posAttr.getZ(i)));
    }
    group.add(
      new THREE.Points(
        new THREE.BufferGeometry().setFromPoints(verts),
        new THREE.PointsMaterial({
          color: 0x818cf8,
          size: 0.02,
          transparent: true,
          opacity: 0.5,
          blending: THREE.AdditiveBlending,
        }),
      ),
    );

    const wire = new THREE.Mesh(
      new THREE.IcosahedronGeometry(2.05, 2),
      new THREE.MeshBasicMaterial({
        color: 0x6366f1,
        wireframe: true,
        transparent: true,
        opacity: 0.08,
      }),
    );
    group.add(wire);

    const ring = new THREE.Mesh(
      new THREE.TorusGeometry(2.35, 0.012, 8, 100),
      new THREE.MeshBasicMaterial({ color: 0x7c3aed, transparent: true, opacity: 0.28 }),
    );
    ring.rotation.x = Math.PI / 2;
    group.add(ring);

    scene.add(new THREE.AmbientLight(0x223344, 0.65));
    const light = new THREE.PointLight(0x6366f1, 1.2, 18);
    light.position.set(2, 1, 4);
    scene.add(light);

    startMs = performance.now();
    let hoverBoost = false;

    const animate = () => {
      frameId = requestAnimationFrame(animate);
      const t = (performance.now() - startMs) * 0.001;
      if (group) {
        group.rotation.y += hoverBoost ? spinY * 2.5 : spinY;
        group.rotation.x += hoverBoost ? spinX * 2 : spinX;
      }
      if (pointsMat) {
        pointsMat.opacity = hoverBoost ? 0.95 : 0.55 + Math.sin(t * 2) * 0.12;
      }
      renderer?.render(scene, camera);
    };
    animate();

    const onResize = () => {
      if (!renderer || !el) return;
      const nw = Math.max(el.clientWidth, 1);
      const nh = Math.max(el.clientHeight, 1);
      camera.aspect = nw / nh;
      camera.updateProjectionMatrix();
      renderer.setSize(nw, nh);
    };
    window.addEventListener("resize", onResize);

    return {
      dispose: () => {
        window.removeEventListener("resize", onResize);
        dispose();
      },
      setHoverBoost: (active: boolean) => {
        hoverBoost = active;
      },
    };
  }

  return { mount };
}
