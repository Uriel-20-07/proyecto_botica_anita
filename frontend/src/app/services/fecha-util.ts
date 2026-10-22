/**
 * El backend guarda las fechas en UTC (columna `timestamp without time zone`
 * alimentada con `LocalDateTime.now(ZoneOffset.UTC)`) y las devuelve en ISO-8601
 * sin zona, por ejemplo "2026-10-22T15:32:10.123456".
 *
 * El navegador interpretaría esa cadena como hora local, lo que genera un
 * desfase de horas (en Lima, 5 h). Por eso aquí siempre se interpreta como UTC.
 */

/** Devuelve la fecha del backend como milisegundos epoch (UTC). */
export function msDesdeFecha(fechaIso: string | Date | null | undefined): number {
  if (!fechaIso) return 0;
  if (fechaIso instanceof Date) return fechaIso.getTime();
  let s = String(fechaIso).trim().replace(' ', 'T');
  // Si no trae zona horaria explícita, se asume UTC.
  if (!/(Z|z|[+-]\d{2}:?\d{2})$/.test(s)) s += 'Z';
  const t = new Date(s).getTime();
  return isNaN(t) ? 0 : t;
}

/** Devuelve la fecha del backend como objeto Date (para mostrarla en hora local). */
export function fechaComoDate(fechaIso: string | Date | null | undefined): Date | null {
  if (!fechaIso) return null;
  return new Date(msDesdeFecha(fechaIso));
}
