import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { PedidoService } from '../../services/pedido.service';
import { AuthModalService } from '../../services/auth-modal.service';
import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';

/**
 * Componente Standalone para mostrar el historial de pedidos de un usuario.
 * 
 * Permite:
 * - Listar pedidos realizados.
 * - Desplegar detalles de cada pedido.
 * - Generar e imprimir un comprobante en PDF con diseño premium.
 * 
 * Si el usuario no ha iniciado sesión, lo redirige al home y abre el modal de login.
 */
@Component({
  selector: 'app-pedidos',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './pedidos.html',
  styleUrls: ['./pedidos.css']
})
export class PedidosComponent implements OnInit {
  /** Array con la lista de pedidos del usuario */
  pedidos = signal<any[]>([]);
  /** Bandera de carga de datos */
  cargando = signal<boolean>(true);
  /** Mensaje de error si falla la llamada */
  error = signal<string>('');
  
  /** Set de IDs de pedidos actualmente expandidos en la UI */
  pedidosExpandidos = signal<Set<number>>(new Set());

  constructor(
    private readonly authService: AuthService,
    private readonly pedidoService: PedidoService,
    private readonly authModalService: AuthModalService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    // Si el usuario no está logueado, redirigir al home y abrir modal de login
    if (!this.authService.isAuthenticated()) {
      this.router.navigate(['/']);
      this.authModalService.open('login');
      return;
    }

    this.cargarPedidos();
  }

  /**
   * Carga los pedidos del usuario desde el backend.
   */
  cargarPedidos(): void {
    this.cargando.set(true);
    this.error.set('');
    
    this.pedidoService.obtenerPedidos().subscribe({
      next: (data) => {
        this.pedidos.set(data);
        this.cargando.set(false);
        // Expandir el primer pedido por defecto si existe alguno
        if (data.length > 0) {
          this.toggleExpansion(data[0].idPedido);
        }
      },
      error: (err) => {
        console.error('Error al cargar pedidos:', err);
        this.error.set(err.error?.error || 'No se pudieron cargar tus pedidos. Por favor, intenta de nuevo.');
        this.cargando.set(false);
      }
    });
  }

  /**
   * Alterna la visibilidad del detalle de un pedido.
   * 
   * @param idPedido ID del pedido a colapsar/expandir.
   */
  toggleExpansion(idPedido: number): void {
    const expandidos = new Set(this.pedidosExpandidos());
    if (expandidos.has(idPedido)) {
      expandidos.delete(idPedido);
    } else {
      expandidos.add(idPedido);
    }
    this.pedidosExpandidos.set(expandidos);
  }

  /**
   * Verifica si un pedido tiene sus detalles visibles.
   * 
   * @param idPedido ID del pedido a verificar.
   * @returns true si está expandido, false si está cerrado.
   */
  isExpandido(idPedido: number): boolean {
    return this.pedidosExpandidos().has(idPedido);
  }

  /**
   * Calcula el subtotal sumando el precio histórico × cantidad para cada ítem del pedido.
   */
  calcularSubtotal(pedido: any): number {
    if (!pedido || !pedido.detalles) return 0;
    return pedido.detalles.reduce((acc: number, item: any) => acc + (item.precioHistorico * item.cantidad), 0);
  }

  /**
   * Determina el costo de envío (S/ 5.00 si el subtotal <= S/ 50.00, gratis si > S/ 50.00).
   */
  calcularEnvio(pedido: any): number {
    if (pedido.esUrgente) return 10;
    const subtotal = this.calcularSubtotal(pedido);
    return subtotal > 50 ? 0 : 5;
  }

  /**
   * Determina el descuento total (diferencia entre subtotal + envío y el total pagado).
   */
  calcularDescuento(pedido: any): number {
    const subtotal = this.calcularSubtotal(pedido);
    const envio = this.calcularEnvio(pedido);
    return Math.max(0, (subtotal + envio) - pedido.total);
  }

  /**
   * Retorna true si hay un descuento sustancial por cupón.
   */
  tieneDescuento(pedido: any): boolean {
    return this.calcularDescuento(pedido) > 0.01;
  }

  /**
   * Formatea un valor numérico a soles con comas para los decimales.
   * Ejemplo: 8.2 -> S/ 8,20
   */
  formatMonto(val: number): string {
    return 'S/ ' + val.toFixed(2).replace('.', ',');
  }

  /**
   * Compila los datos del pedido y descarga un comprobante formal de compra en formato PDF.
   * 
   * @param pedido Objeto de datos del pedido seleccionado.
   * @param index Índice del pedido en la lista.
   */
  descargarPDF(pedido: any, index: number): void {
    // Inicializar documento A4 (portrait, milímetros)
    const doc = new jsPDF({
      orientation: 'portrait',
      unit: 'mm',
      format: 'a4'
    });

    const user = this.authService.getCurrentUser() || { nombre: 'Cliente', apellido: 'FarmaCode', email: '' };

    // --- Definición de colores corporativos ---
    const colorHeaderBg = [15, 23, 42]; // Azul marino oscuro / casi negro #0f172a
    const colorOrange = [232, 93, 4];    // Naranja corporativo #e85d04
    const colorTextOscuro = [15, 23, 42]; // #0f172a
    const colorSlateGray = [100, 116, 139]; // #64748b

    // Calcular el número de boleta incremental
    const nroBoleta = (this.pedidos().length - index).toString().padStart(6, '0');

    // --- Encabezado ---
    // Rectángulo de cabecera azul marino oscuro
    doc.setFillColor(colorHeaderBg[0], colorHeaderBg[1], colorHeaderBg[2]);
    doc.rect(0, 0, 210, 32, 'F');

    // Logo FarmaCode: "Farma" en blanco, "Code" en naranja
    doc.setFont('Helvetica', 'bold');
    doc.setFontSize(22);
    doc.setTextColor(255, 255, 255);
    doc.text('Farma', 15, 17);
    const farmaWidth = doc.getTextWidth('Farma');
    doc.setTextColor(colorOrange[0], colorOrange[1], colorOrange[2]);
    doc.text('Code', 15 + farmaWidth + 1.2, 17);
    
    // Subtexto de cabecera
    doc.setFont('Helvetica', 'normal');
    doc.setFontSize(8.5);
    doc.setTextColor(203, 213, 225); // Slate claro
    doc.text('Expertos en salud digital | 0800-000-000 | www.farmacode.pe', 15, 24);

    // Boleta de Venta (Lado derecho)
    doc.setFont('Helvetica', 'bold');
    doc.setFontSize(9.5);
    doc.setTextColor(colorOrange[0], colorOrange[1], colorOrange[2]);
    doc.text('BOLETA DE VENTA', 195, 15, { align: 'right' });
    
    doc.setFontSize(14);
    doc.setTextColor(255, 255, 255);
    doc.text(`N° ${nroBoleta}`, 195, 23, { align: 'right' });

    // --- Paneles de Cliente e Información de Emisión ---
    // Fondo de paneles gris muy claro
    doc.setFillColor(248, 250, 252);
    doc.rect(15, 40, 90, 23, 'F');
    doc.rect(110, 40, 85, 23, 'F');

    // Panel izquierdo: Cliente
    doc.setFont('Helvetica', 'bold');
    doc.setFontSize(7.5);
    doc.setTextColor(colorSlateGray[0], colorSlateGray[1], colorSlateGray[2]);
    doc.text('CLIENTE', 19, 45);

    doc.setFont('Helvetica', 'bold');
    doc.setFontSize(10.5);
    doc.setTextColor(colorTextOscuro[0], colorTextOscuro[1], colorTextOscuro[2]);
    const nombreCompleto = `${user.nombre} ${user.apellido || ''}`.toUpperCase();
    doc.text(nombreCompleto, 19, 51);

    doc.setFont('Helvetica', 'normal');
    doc.setFontSize(8.2);
    doc.setTextColor(71, 85, 105);
    doc.text(user.email, 19, 57);

    // Panel derecho: Fecha de Emisión y Estado
    doc.setFont('Helvetica', 'bold');
    doc.setFontSize(7.5);
    doc.setTextColor(colorSlateGray[0], colorSlateGray[1], colorSlateGray[2]);
    doc.text('FECHA DE EMISION', 114, 45);

    doc.setFont('Helvetica', 'bold');
    doc.setFontSize(10.5);
    doc.setTextColor(colorTextOscuro[0], colorTextOscuro[1], colorTextOscuro[2]);
    doc.text(this.formatearFechaPDF(pedido.fecha), 114, 51);

    // --- Tabla de Productos ---
    const headers = [['PRODUCTO', 'CANT.', 'PRECIO UNIT.', 'SUBTOTAL']];
    const body = pedido.detalles.map((det: any) => {
      const subtotal = det.precioHistorico * det.cantidad;
      return [
        det.producto.nombre,
        det.cantidad.toString(),
        this.formatMonto(det.precioHistorico),
        this.formatMonto(subtotal)
      ];
    });

    // --- Panel de Establecimiento de Recojo / Envío a Domicilio ---
    doc.setFillColor(248, 250, 252);
    doc.rect(15, 66, 180, 13, 'F');

    doc.setFont('Helvetica', 'bold');
    doc.setFontSize(7.5);
    doc.setTextColor(colorSlateGray[0], colorSlateGray[1], colorSlateGray[2]);
    const panelTitle = pedido.esUrgente ? 'DIRECCIÓN DE ENVÍO URGENTE A DOMICILIO' : 'LUGAR DE RECOJO SELECCIONADO';
    doc.text(panelTitle, 19, 70.5);

    doc.setFont('Helvetica', 'bold');
    doc.setFontSize(8.5);
    doc.setTextColor(colorTextOscuro[0], colorTextOscuro[1], colorTextOscuro[2]);
    const puntoRecojo = pedido.direccionEnvio || (pedido.esUrgente ? 'Domicilio predeterminado en Arequipa' : 'Establecimiento predeterminado en Arequipa');
    doc.text(puntoRecojo, 19, 76);

    autoTable(doc, {
      startY: 82,
      head: headers,
      body: body,
      theme: 'striped',
      headStyles: {
        fillColor: colorHeaderBg as [number, number, number],
        textColor: [255, 255, 255],
        fontStyle: 'bold',
        fontSize: 8.5,
        halign: 'left'
      },
      bodyStyles: {
        fontSize: 8.5,
        textColor: [30, 41, 59],
        valign: 'middle'
      },
      alternateRowStyles: {
        fillColor: [248, 250, 252]
      },
      columnStyles: {
        0: { halign: 'left' },
        1: { cellWidth: 18, halign: 'center' },
        2: { cellWidth: 32, halign: 'right' },
        3: { cellWidth: 32, halign: 'right' }
      },
      styles: {
        lineColor: [226, 232, 240],
        lineWidth: 0.2
      },
      margin: { left: 15, right: 15 }
    });

    // --- Sección de Totales ---
    let currentY = (doc as any).lastAutoTable.finalY + 6;

    const total = pedido.total;
    const subtotalProductos = this.calcularSubtotal(pedido);
    const costoEnvio = this.calcularEnvio(pedido);
    const descuento = this.calcularDescuento(pedido);

    const subtotalProductosNeto = subtotalProductos - descuento;
    const subtotalBase = subtotalProductosNeto / 1.18;
    const igv = subtotalProductosNeto - subtotalBase;

    // 1. Subtotal de los productos (Base Imponible)
    doc.setFont('Helvetica', 'normal');
    doc.setFontSize(9);
    doc.setTextColor(100, 116, 139); // Gray slate
    doc.text('Subtotal (Base Imponible):', 160, currentY, { align: 'right' });
    doc.setFont('Helvetica', 'bold');
    doc.setTextColor(15, 23, 42);
    doc.text(this.formatMonto(subtotalBase), 195, currentY, { align: 'right' });
    currentY += 5;

    // 2. IGV (18%)
    doc.setFont('Helvetica', 'normal');
    doc.setTextColor(100, 116, 139);
    doc.text('IGV (18%):', 160, currentY, { align: 'right' });
    doc.setFont('Helvetica', 'bold');
    doc.setTextColor(15, 23, 42);
    doc.text(this.formatMonto(igv), 195, currentY, { align: 'right' });
    currentY += 5;

    // 3. Descuento por cupón (si aplica)
    if (descuento > 0) {
      doc.setFont('Helvetica', 'normal');
      doc.setTextColor(100, 116, 139);
      doc.text('Descuento:', 160, currentY, { align: 'right' });
      doc.setFont('Helvetica', 'bold');
      doc.setTextColor(185, 28, 28); // Rojo
      doc.text('- ' + this.formatMonto(descuento), 195, currentY, { align: 'right' });
      currentY += 5;
    }

    // 4. Costo de envío (si aplica / si es menor de 50)
    if (costoEnvio > 0) {
      doc.setFont('Helvetica', 'normal');
      doc.setTextColor(100, 116, 139);
      doc.text('Costo de envío:', 160, currentY, { align: 'right' });
      doc.setFont('Helvetica', 'bold');
      doc.setTextColor(15, 23, 42);
      doc.text(this.formatMonto(costoEnvio), 195, currentY, { align: 'right' });
      currentY += 5;
    }

    // Barra TOTAL A PAGAR
    doc.setFillColor(15, 23, 42);
    doc.rect(120, currentY + 3, 75, 9, 'F');

    doc.setFont('Helvetica', 'bold');
    doc.setFontSize(9.5);
    doc.setTextColor(255, 255, 255);
    doc.text('TOTAL A PAGAR:', 123, currentY + 9);

    doc.setFontSize(10.5);
    doc.setTextColor(colorOrange[0], colorOrange[1], colorOrange[2]);
    doc.text(this.formatMonto(total), 192, currentY + 9, { align: 'right' });

    // --- Pie de Página Fijo ---
    const pageHeight = doc.internal.pageSize.height;
    
    // Barra de pie de página azul marino
    doc.setFillColor(colorHeaderBg[0], colorHeaderBg[1], colorHeaderBg[2]);
    doc.rect(0, pageHeight - 16, 210, 16, 'F');

    // Texto del pie de página
    doc.setFont('Helvetica', 'normal');
    doc.setFontSize(8);
    doc.setTextColor(203, 213, 225); // Slate claro
    doc.text('Gracias por su compra en FarmaCode. Este documento es su comprobante de pago.', 105, pageHeight - 10, { align: 'center' });
    doc.text('www.farmacode.pe | atencion@farmacode.pe | Arequipa, Peru', 105, pageHeight - 5, { align: 'center' });

    // Descargar el archivo PDF en el navegador
    doc.save(`FarmaCode_Boleta_Pedido_${nroBoleta}.pdf`);
  }

  /**
   * Formatea la fecha para el PDF en un formato de dd/mm/yyyy hh:mm.
   */
  formatearFechaPDF(fechaStr: string): string {
    if (!fechaStr) return '';
    try {
      const fecha = new Date(fechaStr);
      const dia = fecha.getDate().toString().padStart(2, '0');
      const mes = (fecha.getMonth() + 1).toString().padStart(2, '0');
      const anio = fecha.getFullYear();
      const horas = fecha.getHours().toString().padStart(2, '0');
      const minutos = fecha.getMinutes().toString().padStart(2, '0');
      return `${dia}/${mes}/${anio} ${horas}:${minutos}`;
    } catch {
      return fechaStr;
    }
  }

  /**
   * Formatea la fecha para mostrar en la interfaz HTML.
   */
  formatearFecha(fechaStr: string): string {
    if (!fechaStr) return '';
    try {
      const fecha = new Date(fechaStr);
      return fecha.toLocaleDateString('es-PE', {
        day: 'numeric',
        month: 'long',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
      });
    } catch {
      return fechaStr;
    }
  }
}
