import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { RecetasService, RecetaMedica } from '../../services/recetas.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-carga-receta',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './carga-receta.component.html',
  styleUrl: './carga-receta.component.css'
})
export class CargaRecetaComponent implements OnInit {
  nuevaReceta: RecetaMedica = {
    pacienteId: '',
    medicoId: '',
    medicamentos: [{ nombre: '', dosis: '' }],
    fechaEmision: new Date().toISOString().split('T')[0],
    estado: 'en_espera',
    documentoUrl: ''
  };

  documentoFile: File | null = null;
  enviando = false;
  mensajeExito: string | null = null;
  mensajeError: string | null = null;

  constructor(
    private recetasService: RecetasService,
    private router: Router,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    if (!this.authService.isAuthenticated()) {
      this.router.navigate(['/']);
      return;
    }
    const usuario = this.authService.getCurrentUser();
    if (usuario?.id) {
      this.nuevaReceta.pacienteId = usuario.id.toString();
    }
  }

  agregarMedicamento(): void {
    this.nuevaReceta.medicamentos.push({ nombre: '', dosis: '' });
  }

  eliminarMedicamento(index: number): void {
    if (this.nuevaReceta.medicamentos.length > 1) {
      this.nuevaReceta.medicamentos.splice(index, 1);
    }
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.documentoFile = input.files[0];
      this.nuevaReceta.documentoUrl = input.files[0].name;
    }
  }

  enviarReceta(): void {
    this.mensajeExito = null;
    this.mensajeError = null;

    const medicamentosValidos = this.nuevaReceta.medicamentos.every(
      (m: { nombre: string; dosis: string }) => m.nombre.trim() && m.dosis.trim()
    );

    if (!this.nuevaReceta.pacienteId.trim() || !this.nuevaReceta.medicoId.trim() || !medicamentosValidos) {
      this.mensajeError = 'Por favor, complete todos los campos obligatorios.';
      return;
    }

    this.enviando = true;

    if (this.documentoFile) {
      this.recetasService.subirDocumento(this.documentoFile).subscribe({
        next: (response: { url: string }) => {
          this.nuevaReceta.documentoUrl = response.url;
          this.guardarRecetaBase();
        },
        error: (err: any) => {
          this.enviando = false;
          this.mensajeError = 'Error al subir la imagen del documento. Inténtelo de nuevo.';
          console.error('Error al subir documento:', err);
        }
      });
    } else {
      this.guardarRecetaBase();
    }
  }

  private guardarRecetaBase(): void {
    this.recetasService.cargarReceta(this.nuevaReceta).subscribe({
      next: (receta: RecetaMedica) => {
        this.enviando = false;
        this.mensajeExito = `Receta enviada con éxito y en espera de revisión.`;
        this.resetFormulario();
        setTimeout(() => this.router.navigate(['/']), 1500);
      },
      error: (err: any) => {
        this.enviando = false;
        this.mensajeError = 'Error al enviar la receta. Inténtelo de nuevo.';
        console.error('Error al cargar receta:', err);
      }
    });
  }

  private resetFormulario(): void {
    this.documentoFile = null;
    const usuario = this.authService.getCurrentUser();
    this.nuevaReceta = {
      pacienteId: usuario?.id ? usuario.id.toString() : '',
      medicoId: '',
      medicamentos: [{ nombre: '', dosis: '' }],
      fechaEmision: new Date().toISOString().split('T')[0],
      estado: 'en_espera',
      documentoUrl: ''
    };
  }
}