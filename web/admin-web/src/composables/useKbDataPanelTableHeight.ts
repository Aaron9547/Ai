import { onBeforeUnmount, ref, watch, type Ref } from "vue";

export type KbDataPanelTableHeightOptions = {
  /** 表格外壳内除表体槽位外预留像素（分页、tabs 头等） */
  reservedPx?: number;
  min?: number;
  max?: number;
};

/**
 * 弹窗内 el-table 固定高度：随 shell 区域 ResizeObserver 自适应，表体滚动、弹窗不无限增高。
 */
export function useKbDataPanelTableHeight(
  open: Ref<boolean>,
  options: KbDataPanelTableHeightOptions = {},
) {
  const shellRef = ref<HTMLElement | null>(null);
  const tableHeight = ref(320);
  const minH = options.min ?? 220;
  const maxH = options.max ?? 520;

  let ro: ResizeObserver | null = null;

  function measure() {
    const shell = shellRef.value;
    if (!shell) return;
    const slot = shell.querySelector(".kb-data-panel-dialog__table-slot") as HTMLElement | null;
    const h = slot?.clientHeight ?? shell.clientHeight;
    const next = Math.max(minH, Math.min(maxH, Math.floor(h - 2)));
    if (next > 0) tableHeight.value = next;
  }

  function bind() {
    unbind();
    const shell = shellRef.value;
    if (!shell) return;
    measure();
    if (typeof ResizeObserver === "undefined") return;
    ro = new ResizeObserver(() => measure());
    ro.observe(shell);
    const slot = shell.querySelector(".kb-data-panel-dialog__table-slot");
    if (slot) ro.observe(slot);
  }

  function unbind() {
    ro?.disconnect();
    ro = null;
  }

  watch(
    open,
    (isOpen) => {
      if (isOpen) {
        requestAnimationFrame(() => {
          requestAnimationFrame(() => bind());
        });
      } else {
        unbind();
      }
    },
  );

  onBeforeUnmount(unbind);

  return { shellRef, tableHeight, remeasure: measure };
}
