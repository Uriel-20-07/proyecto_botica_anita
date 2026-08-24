import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-recuperar-contrasena',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './recuperar-contrasena.component.html',
  styleUrl: './recuperar-contrasena.component.css'
})
export class RecuperarContraseñaComponent {
  form!: FormGroup;
  loading = false;
  submitted = false;
  error = '';
  success = '';
  step: number = 1; // 1: solicitar, 2: reset
  token: string = '';

  constructor(
    private formBuilder: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute
  ) {
    // Verificar si viene con token en la URL
    this.route.params.subscribe(params => {
      if (params['token']) {
        this.token = params['token'];
        this.validateToken();
      } else {
        this.initRequestForm();
      }
    });
  }

  initRequestForm(): void {
    this.step = 1;
    this.form = this.formBuilder.group({
      email: ['', [Validators.required, Validators.pattern(/^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9_.-]+\.[a-zA-Z]{2,}$/)]]
    });
  }

  initResetForm(): void {
    this.step = 2;
    this.form = this.formBuilder.group({
      password: ['', [Validators.required, Validators.minLength(6)]],
      confirmPassword: ['', [Validators.required]]
    }, { validators: this.passwordMatchValidator });
  }

  passwordMatchValidator(group: FormGroup): { [key: string]: any } | null {
    const password = group.get('password');
    const confirmPassword = group.get('confirmPassword');
    
    if (password && confirmPassword && password.value !== confirmPassword.value) {
      return { 'passwordMismatch': true };
    }
    return null;
  }

  validateToken(): void {
    this.loading = true;
    this.authService.validarToken(this.token).subscribe({
      next: (response) => {
        this.initResetForm();
        this.loading = false;
      },
      error: (error) => {
        this.error = 'El enlace de recuperación es inválido o ha expirado';
        this.loading = false;
      }
    });
  }

  get f() {
    return this.form.controls;
  }

  onSubmit(): void {
    this.submitted = true;
    this.error = '';
    this.success = '';

    if (this.form.invalid) {
      return;
    }

    this.loading = true;

    if (this.step === 1) {
      // Solicitar recuperación
      this.authService.solicitarRecuperacion(this.f['email'].value).subscribe({
        next: (response) => {
          this.success = 'Te hemos enviado un correo con instrucciones para recuperar tu contraseña';
          setTimeout(() => {
            this.router.navigate(['/login']);
          }, 3000);
        },
        error: (error) => {
          this.error = 'Error al procesar la solicitud';
          this.loading = false;
        }
      });
    } else if (this.step === 2) {
      // Cambiar contraseña
      this.authService.cambiarContraseña(this.token, this.f['password'].value).subscribe({
        next: (response) => {
          this.success = 'Contraseña cambiada exitosamente. Redirigiendo a login...';
          setTimeout(() => {
            this.router.navigate(['/login']);
          }, 2000);
        },
        error: (error) => {
          this.error = error.error?.error || 'Error al cambiar la contraseña';
          this.loading = false;
        }
      });
    }
  }

  goToLogin(): void {
    this.router.navigate(['/login']);
  }
}
