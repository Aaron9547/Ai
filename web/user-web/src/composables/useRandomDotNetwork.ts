import { nextTick, onUnmounted, type Ref, shallowRef, watch } from "vue";

/** 随机点 + 近距离连线（参考 rstyro/html5 randomDot） */
export interface RandomDotNetworkOptions {
  dotCount?: number;
  /** 两点连线最大轴差（原 distance: 50） */
  linkDistance?: number;
  /** 鼠标附近才绘制连线的半径 */
  mouseRadius?: number;
  lineWidth?: number;
  /** 远离鼠标时的连线色 */
  baseStroke?: string;
  minColor?: number;
}

interface RgbColor {
  r: number;
  g: number;
  b: number;
  style: string;
}

interface Dot {
  x: number;
  y: number;
  vx: number;
  vy: number;
  radius: number;
  color: RgbColor;
  draw(ctx: CanvasRenderingContext2D): void;
}

function colorValue(min: number): number {
  return Math.floor(Math.random() * 255 + min);
}

function createColorStyle(r: number, g: number, b: number, alpha = 0.8): string {
  return `rgba(${r},${g},${b},${alpha})`;
}

function mixComponents(comp1: number, weight1: number, comp2: number, weight2: number): number {
  return (comp1 * weight1 + comp2 * weight2) / (weight1 + weight2);
}

function averageColorStyles(dot1: Dot, dot2: Dot): string {
  const color1 = dot1.color;
  const color2 = dot2.color;
  const r = mixComponents(color1.r, dot1.radius, color2.r, dot2.radius);
  const g = mixComponents(color1.g, dot1.radius, color2.g, dot2.radius);
  const b = mixComponents(color1.b, dot1.radius, color2.b, dot2.radius);
  return createColorStyle(Math.floor(r), Math.floor(g), Math.floor(b));
}

function makeColor(min = 0): RgbColor {
  const r = colorValue(min);
  const g = colorValue(min);
  const b = colorValue(min);
  return { r, g, b, style: createColorStyle(r, g, b) };
}

function createDot(w: number, h: number, minColor: number): Dot {
  const color = makeColor(minColor);
  const dot: Dot = {
    x: Math.random() * w,
    y: Math.random() * h,
    vx: -0.5 + Math.random(),
    vy: -0.5 + Math.random(),
    radius: Math.random() * 2,
    color,
    draw(ctx) {
      ctx.beginPath();
      ctx.fillStyle = color.style;
      ctx.arc(dot.x, dot.y, dot.radius, 0, Math.PI * 2, false);
      ctx.fill();
    },
  };
  return dot;
}

/**
 * Canvas 随机粒子网络背景；鼠标附近显示彩色连线。
 * @see https://rstyro.github.io/html5/randomDot/index.html
 */
export function useRandomDotNetwork(
  canvasRef: Ref<HTMLCanvasElement | null>,
  active: Ref<boolean>,
  options: RandomDotNetworkOptions = {},
) {
  const {
    dotCount = 150,
    linkDistance = 50,
    mouseRadius = 100,
    lineWidth = 0.3,
    baseStroke = "rgba(150,150,150,0.35)",
    minColor = 0,
  } = options;

  const mouse = shallowRef({ x: 0, y: 0 });
  let dots: Dot[] = [];
  let rafId = 0;
  let running = false;

  function resize() {
    const canvas = canvasRef.value;
    if (!canvas) return;
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;
    if (mouse.value.x === 0 && mouse.value.y === 0) {
      mouse.value = {
        x: (canvas.width * 30) / 100,
        y: (canvas.height * 30) / 100,
      };
    }
  }

  function seedDots() {
    const canvas = canvasRef.value;
    if (!canvas) return;
    dots = [];
    for (let i = 0; i < dotCount; i++) {
      dots.push(createDot(canvas.width, canvas.height, minColor));
    }
  }

  function moveDots() {
    const canvas = canvasRef.value;
    if (!canvas) return;
    const w = canvas.width;
    const h = canvas.height;
    for (const dot of dots) {
      if (dot.y < 0 || dot.y > h) dot.vy = -dot.vy;
      else if (dot.x < 0 || dot.x > w) dot.vx = -dot.vx;
      dot.x += dot.vx;
      dot.y += dot.vy;
    }
  }

  function connectDots(ctx: CanvasRenderingContext2D) {
    const mx = mouse.value.x;
    const my = mouse.value.y;
    const d = linkDistance;
    const r = mouseRadius;

    for (let i = 0; i < dots.length; i++) {
      const iDot = dots[i]!;
      for (let j = i + 1; j < dots.length; j++) {
        const jDot = dots[j]!;
        const dx = iDot.x - jDot.x;
        const dy = iDot.y - jDot.y;
        if (Math.abs(dx) >= d || Math.abs(dy) >= d) continue;

        const nearMouse =
          Math.abs(iDot.x - mx) < r &&
          Math.abs(iDot.y - my) < r &&
          Math.abs(jDot.x - mx) < r &&
          Math.abs(jDot.y - my) < r;
        if (!nearMouse) continue;

        ctx.beginPath();
        ctx.strokeStyle = averageColorStyles(iDot, jDot);
        ctx.moveTo(iDot.x, iDot.y);
        ctx.lineTo(jDot.x, jDot.y);
        ctx.stroke();
      }
    }
  }

  function drawDots(ctx: CanvasRenderingContext2D) {
    for (const dot of dots) dot.draw(ctx);
  }

  function frame() {
    const canvas = canvasRef.value;
    if (!canvas || !running) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.lineWidth = lineWidth;
    ctx.strokeStyle = baseStroke;
    moveDots();
    connectDots(ctx);
    drawDots(ctx);
    rafId = requestAnimationFrame(frame);
  }

  function onPointerMove(e: PointerEvent) {
    mouse.value = { x: e.clientX, y: e.clientY };
  }

  function onPointerLeave() {
    const canvas = canvasRef.value;
    if (!canvas) return;
    mouse.value = { x: canvas.width / 2, y: canvas.height / 2 };
  }

  function start() {
    if (running) return;
    const canvas = canvasRef.value;
    if (!canvas) return;
    running = true;
    resize();
    seedDots();
    window.addEventListener("resize", resize);
    window.addEventListener("pointermove", onPointerMove);
    window.addEventListener("pointerleave", onPointerLeave);
    rafId = requestAnimationFrame(frame);
  }

  function stop() {
    running = false;
    if (rafId) cancelAnimationFrame(rafId);
    rafId = 0;
    window.removeEventListener("resize", resize);
    window.removeEventListener("pointermove", onPointerMove);
    window.removeEventListener("pointerleave", onPointerLeave);
    const canvas = canvasRef.value;
    const ctx = canvas?.getContext("2d");
    if (canvas && ctx) ctx.clearRect(0, 0, canvas.width, canvas.height);
    dots = [];
  }

  watch(
    active,
    (on) => {
      if (on) void nextTick(() => start());
      else stop();
    },
    { immediate: true },
  );

  watch(canvasRef, (el, prev) => {
    if (!el && prev) stop();
    else if (el && active.value) void nextTick(() => start());
  });

  onUnmounted(stop);

  return { start, stop };
}
