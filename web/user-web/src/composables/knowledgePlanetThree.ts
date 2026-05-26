import * as THREE from "three";

const FRESNEL_VERTEX = `
varying vec3 vNormal;
varying vec3 vPositionNormal;
void main() {
  vNormal = normalize(normalMatrix * normal);
  vec4 mvPos = modelViewMatrix * vec4(position, 1.0);
  vPositionNormal = normalize(mvPos.xyz);
  gl_Position = projectionMatrix * mvPos;
}
`;

const FRESNEL_FRAGMENT = `
uniform vec3 glowColor;
uniform float coefficient;
uniform float power;
varying vec3 vNormal;
varying vec3 vPositionNormal;
void main() {
  float intensity = pow(coefficient - dot(vNormal, vPositionNormal), power);
  gl_FragColor = vec4(glowColor, clamp(intensity, 0.0, 1.0) * 0.9);
}
`;

export function createFresnelPlanetObject(hexColor: string, displaySize: number): THREE.Object3D {
  const c = new THREE.Color(hexColor);
  const radius = Math.max(3, Math.min(10, displaySize * 0.32));
  const group = new THREE.Group();

  const core = new THREE.Mesh(
    new THREE.IcosahedronGeometry(radius, 3),
    new THREE.ShaderMaterial({
      uniforms: {
        glowColor: { value: c },
        coefficient: { value: 1.05 },
        power: { value: 2.2 },
      },
      vertexShader: FRESNEL_VERTEX,
      fragmentShader: FRESNEL_FRAGMENT,
      transparent: true,
      blending: THREE.AdditiveBlending,
      side: THREE.DoubleSide,
      depthWrite: false,
    }),
  );
  group.add(core);

  const wire = new THREE.Mesh(
    new THREE.IcosahedronGeometry(radius * 1.08, 2),
    new THREE.MeshBasicMaterial({
      color: c,
      wireframe: true,
      transparent: true,
      opacity: 0.12,
    }),
  );
  group.add(wire);

  const ring = new THREE.Mesh(
    new THREE.TorusGeometry(radius * 1.45, radius * 0.04, 8, 64),
    new THREE.MeshBasicMaterial({ color: c, transparent: true, opacity: 0.35 }),
  );
  ring.rotation.x = Math.PI / 2.15;
  group.add(ring);

  return group;
}

export function createKnowledgeCrystalObject(hexColor: string): THREE.Object3D {
  const c = new THREE.Color(hexColor);
  const geom = new THREE.OctahedronGeometry(2.4, 0);
  const mat = new THREE.MeshStandardMaterial({
    color: c,
    emissive: c,
    emissiveIntensity: 0.45,
    metalness: 0.35,
    roughness: 0.25,
    transparent: true,
    opacity: 0.72,
  });
  const mesh = new THREE.Mesh(geom, mat);
  const glow = new THREE.Mesh(
    new THREE.OctahedronGeometry(3.1, 0),
    new THREE.MeshBasicMaterial({
      color: c,
      transparent: true,
      opacity: 0.12,
      side: THREE.BackSide,
    }),
  );
  mesh.add(glow);
  return mesh;
}

export function addUniverseStarfield(scene: THREE.Scene): void {
  const count = 1400;
  const positions = new Float32Array(count * 3);
  for (let i = 0; i < count; i++) {
    const r = 350 + Math.random() * 650;
    const theta = Math.random() * Math.PI * 2;
    const phi = Math.acos(2 * Math.random() - 1);
    positions[i * 3] = r * Math.sin(phi) * Math.cos(theta);
    positions[i * 3 + 1] = r * Math.sin(phi) * Math.sin(theta);
    positions[i * 3 + 2] = r * Math.cos(phi);
  }
  const geom = new THREE.BufferGeometry();
  geom.setAttribute("position", new THREE.BufferAttribute(positions, 3));
  scene.add(
    new THREE.Points(
      geom,
      new THREE.PointsMaterial({
        color: 0x8899bb,
        size: 1.1,
        transparent: true,
        opacity: 0.5,
        sizeAttenuation: true,
        blending: THREE.AdditiveBlending,
      }),
    ),
  );
}
