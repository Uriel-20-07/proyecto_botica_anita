import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { AdminService } from '../../services/admin.service';
import { AuthModalMode, AuthModalService } from '../../services/auth-modal.service';

@Component({
  selector: 'app-auth-modal',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './auth-modal.component.html',
  styleUrl: './auth-modal.component.css'
})
export class AuthModalComponent implements OnInit, OnDestroy {
  isOpen = false;
  mode: AuthModalMode = 'login';
  loginForm: FormGroup;
  registroForm: FormGroup;
  recoveryForm: FormGroup;
  loading = false;
  error = '';
  success = '';
  showLoginPassword = false;
  showRegisterPassword = false;
  showRegisterConfirmPassword = false;
  showRecoveryPassword = false;
  recoveryStep: 1 | 2 = 1;

  private readonly subscriptions = new Subscription();

  constructor(
    private readonly formBuilder: FormBuilder,
    private readonly authService: AuthService,
    private readonly authModalService: AuthModalService,
    private readonly adminService: AdminService,
    private readonly router: Router
  ) {
    const emailPattern = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9_.-]+\.[a-zA-Z]{2,}$/;

    this.loginForm = this.formBuilder.group({
      email: ['', [Validators.required, Validators.pattern(emailPattern)]],
      password: ['', [Validators.required, Validators.minLength(6)]]
    });

    this.registroForm = this.formBuilder.group({
      nombre: ['', [Validators.required]],
      apellido: ['', [Validators.required]],
      email: ['', [Validators.required, Validators.pattern(emailPattern)]],
      telefono: [''],
      password: ['', [Validators.required, Validators.minLength(6)]],
      confirmPassword: ['', [Validators.required]]
    }, { validators: this.passwordMatchValidator });

    this.recoveryForm = this.formBuilder.group({
      email: ['', [Validators.required, Validators.pattern(emailPattern)]],
      token: ['', [Validators.required]],
      password: ['', [Validators.required, Validators.minLength(6)]],
      confirmPassword: ['', [Validators.required]]
    }, { validators: this.recoveryPasswordMatchValidator });
  }

  ngOnInit(): void {
    this.subscriptions.add(
      this.authModalService.open$.subscribe((isOpen) => {
        this.isOpen = isOpen;
        if (!isOpen) {
          this.loading = false;
          this.error = '';
          this.success = '';
          this.loginForm.reset();
          this.registroForm.reset();
          this.showLoginPassword = false;
          this.showRegisterPassword = false;
          this.showRegisterConfirmPassword = false;
          this.recoveryForm.reset();
          this.showRecoveryPassword = false;
          this.recoveryStep = 1;
        }
      })
    );

    this.subscriptions.add(
      this.authModalService.mode$.subscribe((mode) => {
        this.mode = mode;
      })
    );
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  get loginControls() {
    return this.loginForm.controls;
  }

  get registroControls() {
    return this.registroForm.controls;
  }

  get recoveryControls() {
    return this.recoveryForm.controls;
  }

  passwordMatchValidator(group: FormGroup): { [key: string]: any } | null {
    const password = group.get('password');
    const confirmPassword = group.get('confirmPassword');

    if (password && confirmPassword && password.value !== confirmPassword.value) {
      return { passwordMismatch: true };
    }
    return null;
  }

  recoveryPasswordMatchValidator(group: FormGroup): { [key: string]: any } | null {
    const password = group.get('password');
    const confirmPassword = group.get('confirmPassword');

    if (password && confirmPassword && password.value !== confirmPassword.value) {
      return { passwordMismatch: true };
    }
    return null;
  }

  close(): void {
    this.authModalService.close();
  }

  setMode(mode: AuthModalMode): void {
    this.authModalService.setMode(mode);
    this.error = '';
    this.success = '';
  }

  openRecovery(): void {
    this.setMode('recuperar');
    this.loading = false;
    this.error = '';
    this.success = '';
    this.recoveryStep = 1;
    this.recoveryForm.reset();
  }

  backToLogin(): void {
    this.setMode('login');
  }

  toggleLoginPassword(): void {
    this.showLoginPassword = !this.showLoginPassword;
  }

  toggleRegisterPassword(): void {
    this.showRegisterPassword = !this.showRegisterPassword;
  }

  toggleRegisterConfirmPassword(): void {
    this.showRegisterConfirmPassword = !this.showRegisterConfirmPassword;
  }

  toggleRecoveryPassword(): void {
    this.showRecoveryPassword = !this.showRecoveryPassword;
  }

  submitLogin(): void {
    this.error = '';

    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    const email = this.loginControls['email'].value;
    const password = this.loginControls['password'].value;
    this.loading = true;

    // Detectar si es un correo corporativo
    if (email.endsWith('@correo_corp.com')) {
      this.adminService.login(email, password).subscribe({
        next: () => {
          this.success = 'Sesión corporativa iniciada. Redirigiendo...';
          this.loading = false;
          const user = this.adminService.getCurrentUser();
          setTimeout(() => {
            this.close();
            if (user && user.rol === 'admin') {
              this.router.navigate(['/dashboard/admin']);
            } else if (user && user.rol === 'vendedor') {
              this.router.navigate(['/dashboard/vendedor']);
            }
          }, 800);
        },
        error: (err) => {
          this.error = err.error?.error || 'Credenciales corporativas incorrectas';
          this.loading = false;
        }
      });
      return;
    }

    // Flujo de login para clientes normales
    this.authService.login(email, password).subscribe({
      next: () => {
        this.success = 'Sesión iniciada correctamente.';
        this.loading = false;
        setTimeout(() => this.close(), 600);
      },
      error: (response) => {
        this.error = response.error?.error || 'Error al iniciar sesión';
        this.loading = false;
      }
    });
  }

  submitRegistro(): void {
    this.error = '';
    this.success = '';

    if (this.registroForm.invalid) {
      this.registroForm.markAllAsTouched();
      return;
    }

    this.loading = true;
    this.authService.registro(
      this.registroControls['email'].value,
      this.registroControls['password'].value,
      this.registroControls['nombre'].value,
      this.registroControls['apellido'].value,
      this.registroControls['telefono'].value
    ).subscribe({
      next: () => {
        this.success = 'Registro exitoso. Ya puedes iniciar sesión.';
        this.loading = false;
        this.setMode('login');
      },
      error: (response) => {
        this.error = response.error?.error || 'Error al registrar usuario';
        this.loading = false;
      }
    });
  }

  submitRecovery(): void {
    this.error = '';
    this.success = '';

    if (this.recoveryStep === 1) {
      if (this.recoveryControls['email'].invalid) {
        this.recoveryControls['email'].markAsTouched();
        return;
      }

      this.loading = true;
      this.authService.solicitarRecuperacion(this.recoveryControls['email'].value).subscribe({
        next: () => {
          this.recoveryStep = 2;
          this.success = 'Se envió un token a tu correo. Escríbelo junto con tu nueva contraseña.';
          this.loading = false;
        },
        error: (response) => {
          this.error = response.error?.error || 'No se pudo enviar el token de recuperación';
          this.loading = false;
        }
      });
      return;
    }

    if (this.recoveryForm.invalid) {
      this.recoveryForm.markAllAsTouched();
      return;
    }

    this.loading = true;
    this.authService.cambiarContraseña(
      this.recoveryControls['token'].value,
      this.recoveryControls['password'].value
    ).subscribe({
      next: () => {
        this.success = 'Contraseña actualizada. Ya puedes iniciar sesión.';
        this.loading = false;
        this.setMode('login');
      },
      error: (response) => {
        this.error = response.error?.error || 'No se pudo cambiar la contraseña';
        this.loading = false;
      }
    });
  }
}
