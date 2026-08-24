import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  FormsModule,
  ReactiveFormsModule,
  FormBuilder,
  FormGroup,
  Validators
} from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { AuthService } from '../../services/auth.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-perfil',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './perfil.html',
  styleUrls: ['./perfil.css']
})
export class PerfilComponent implements OnInit {

  private apiUrl = `${environment.apiUrl}/api/auth`;

  seccionActiva = signal<'info' | 'password'>('info');
  mensajeExito = '';
  mensajeError = '';
  fotoPreview = '';
  guardando = false;

  infoForm!: FormGroup;
  passwordForm!: FormGroup;

  constructor(
    private authService: AuthService,
    private router: Router,
    private fb: FormBuilder,
    private http: HttpClient
  ) {}

  ngOnInit(): void {
    const user = this.authService.getCurrentUser();

    if (!user) {
      this.router.navigate(['/login']);
      return;
    }

    // Campos alineados con lo que devuelve el backend
    this.infoForm = this.fb.group({
      nombre:         [user.nombre   || '', Validators.required],
      apellido:       [user.apellido || ''],
      email:          [user.email    || '', [Validators.required, Validators.pattern(/^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9_.-]+\.[a-zA-Z]{2,}$/)]]
    });

    this.passwordForm = this.fb.group({
      actual:    ['', Validators.required],
      nueva:     ['', [Validators.required, Validators.minLength(6)]],
      confirmar: ['', Validators.required]
    }, { validators: this.passwordsIguales });
  }

  passwordsIguales(group: FormGroup) {
    const nueva     = group.get('nueva')?.value;
    const confirmar = group.get('confirmar')?.value;
    return nueva === confirmar ? null : { noCoinciden: true };
  }

  cambiarSeccion(seccion: 'info' | 'password'): void {
    this.seccionActiva.set(seccion);
    this.mensajeExito = '';
    this.mensajeError = '';
  }

  guardarInfo(): void {
    if (this.infoForm.invalid) return;
    this.guardando = true;

    // Actualiza localStorage con los nuevos datos
    // Cuando el backend tenga PUT /api/auth/perfil, conectar aquí
    const usuarioActualizado = {
      ...this.authService.getCurrentUser(),
      ...this.infoForm.value
    };
    localStorage.setItem('usuario', JSON.stringify(usuarioActualizado));

    setTimeout(() => {
      this.guardando = false;
      this.mostrarExito('Datos actualizados correctamente');
    }, 800);
  }

  guardarPassword(): void {
    if (this.passwordForm.invalid) return;

    if (this.passwordForm.hasError('noCoinciden')) {
      this.mostrarError('Las contraseñas no coinciden');
      return;
    }

    this.guardando = true;

    const token = this.authService.getToken();
    const headers = new HttpHeaders({ Authorization: `Bearer ${token}` });

    // Conectado al endpoint real del backend
    this.http.post(`${this.apiUrl}/cambiar-contrasena-autenticado`, {
      contraseñaActual: this.passwordForm.value.actual,
      nuevaContraseña:  this.passwordForm.value.nueva
    }, { headers }).subscribe({
      next: () => {
        this.passwordForm.reset();
        this.guardando = false;
        this.mostrarExito('Contraseña actualizada correctamente');
      },
      error: (err) => {
        this.guardando = false;
        this.mostrarError(
          err.error?.error || 'Error al actualizar contraseña'
        );
      }
    });
  }

  onFotoChange(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (file) {
      const reader = new FileReader();
      reader.onload = (e) => {
        this.fotoPreview = e.target?.result as string;
      };
      reader.readAsDataURL(file);
    }
  }

  cerrarSesion(): void {
    this.authService.logout();
    this.router.navigate(['/']);
  }

  mostrarExito(msg: string): void {
    this.mensajeExito = msg;
    this.mensajeError = '';
    setTimeout(() => this.mensajeExito = '', 3500);
  }

  mostrarError(msg: string): void {
    this.mensajeError = msg;
    this.mensajeExito = '';
    setTimeout(() => this.mensajeError = '', 3500);
  }

  get iniciales(): string {
    const n = this.infoForm?.get('nombre')?.value?.[0]   || '';
    const a = this.infoForm?.get('apellido')?.value?.[0] || '';
    return (n + a).toUpperCase() || 'U';
  }

  get nombreCompleto(): string {
    const n = this.infoForm?.get('nombre')?.value   || '';
    const a = this.infoForm?.get('apellido')?.value || '';
    return `${n} ${a}`.trim();
  }
}