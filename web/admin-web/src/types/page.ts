/** 与 MyBatis-Plus `Page` JSON 序列化字段对齐 */
export interface MybatisPage<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages?: number;
}
