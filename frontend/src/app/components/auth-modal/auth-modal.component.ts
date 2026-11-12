import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { AdminService } from '../../services/admin.service';
import { AuthModalMode, AuthModalService } from '../../services/auth-modal.service';

/**
 * Progreso del registro que se guarda temporalmente en sessionStorage para
 * sobrevivir a una recarga de página. Se borra al cerrar el modal, al cambiar
 * de pestaña o al completar el registro.
 */
interface ProgresoRegistro {
  email: string;
  password: string;
  confirmPassword: string;
  nombre: string;
  apellido: string;
  tipoDocumento: string;
  numeroDocumento: string;
  telefono: string;
  aceptaTerminos: boolean;
  paso: number;
  codigoEnviadoEn?: number;
  guardadoEn: number;
}

@Component({
  selector: 'app-auth-modal',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './auth-modal.component.html',
  styleUrl: './auth-modal.component.css'
})
export class AuthModalComponent implements OnInit, OnDestroy {
  /** Clave de sessionStorage donde se guarda el progreso del registro. */
  private static readonly CLAVE_PROGRESO = 'registroPendiente';

  /** Vigencia máxima del progreso guardado: 30 minutos. */
  private static readonly PROGRESO_TTL_MS = 30 * 60 * 1000;

  isOpen = false;
  mode: AuthModalMode = 'login';
  loginForm: FormGroup;
  credencialesForm: FormGroup;
  datosPersonalesForm: FormGroup;
  verificacionForm: FormGroup;
  recoveryForm: FormGroup;
  loading = false;
  enviandoCodigo = false;
  reenvioCooldown = 0;
  error = '';
  success = '';
  showLoginPassword = false;
  showRegisterPassword = false;
  showRegisterConfirmPassword = false;
  showRecoveryPassword = false;
  recoveryStep: 1 | 2 = 1;
  registroStep: 1 | 2 | 3 = 1;

  private readonly subscriptions = new Subscription();
  private cooldownInterval?: ReturnType<typeof setInterval>;

  /** Momento en que se envió (o reutilizó) el último código, para el cooldown. */
  private codigoEnviadoEn = 0;

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

    this.credencialesForm = this.formBuilder.group({
      email: ['', [Validators.required, Validators.pattern(emailPattern)]],
      password: ['', [Validators.required, Validators.minLength(6)]],
      confirmPassword: ['', [Validators.required]]
    }, { validators: this.passwordMatchValidator });

    this.datosPersonalesForm = this.formBuilder.group({
      nombre: ['', [Validators.required]],
      apellido: ['', [Validators.required]],
      tipoDocumento: ['DNI', [Validators.required]],
      numeroDocumento: ['', [Validators.required, Validators.pattern(/^\d{8}$/)]],
      telefono: [''],
      aceptaTerminos: [false, [Validators.requiredTrue]]
    });

    this.verificacionForm = this.formBuilder.group({
      codigo: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]]
    });

    this.recoveryForm = this.formBuilder.group({
      email: ['', [Validators.required, Validators.pattern(emailPattern)]],
      token: ['', [Validators.required]],
      password: ['', [Validators.required, Validators.minLength(6)]],
      confirmPassword: ['', [Validators.required]]
    }, { validators: this.recoveryPasswordMatchValidator });

    this.subscriptions.add(
      this.datosPersonalesForm.get('tipoDocumento')!.valueChanges.subscribe((tipo) => {
        this.actualizarValidacionDocumento(tipo);
      })
    );
  }

  ngOnInit(): void {
    // Se restaura antes de suscribirse a open$: si había un registro en curso,
    // el modal se reabre directamente y el manejador de cierre no lo resetea.
    this.restaurarProgreso();

    this.subscriptions.add(
      this.authModalService.open$.subscribe((isOpen) => {
        this.isOpen = isOpen;
        if (!isOpen) {
          this.loading = false;
          this.error = '';
          this.success = '';
          this.loginForm.reset();
          this.resetRegistro();
          this.showLoginPassword = false;
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

    // Guarda el avance del registro para poder retomarlo tras una recarga.
    this.subscriptions.add(
      this.credencialesForm.valueChanges.subscribe(() => this.guardarProgreso())
    );
    this.subscriptions.add(
      this.datosPersonalesForm.valueChanges.subscribe(() => this.guardarProgreso())
    );
  }

  ngOnDestroy(): void {
    this.detenerCooldown();
    this.subscriptions.unsubscribe();
  }

  get loginControls() {
    return this.loginForm.controls;
  }

  get credencialesControls() {
    return this.credencialesForm.controls;
  }

  get datosPersonalesControls() {
    return this.datosPersonalesForm.controls;
  }

  get verificacionControls() {
    return this.verificacionForm.controls;
  }

  get recoveryControls() {
    return this.recoveryForm.controls;
  }

  get maxLongitudDocumento(): number {
    return this.datosPersonalesControls['tipoDocumento'].value === 'CE' ? 9 : 8;
  }

  /** Deja solo dígitos y corta al máximo permitido según el tipo de documento. */
  soloNumeros(event: Event): void {
    const input = event.target as HTMLInputElement;
    const limpio = input.value.replace(/\D/g, '').slice(0, this.maxLongitudDocumento);
    if (input.value !== limpio) {
      input.value = limpio;
    }
    this.datosPersonalesControls['numeroDocumento'].setValue(limpio);
  }

  /** Deja solo dígitos y corta el código de verificación a 6 caracteres. */
  soloNumerosCodigo(event: Event): void {
    const input = event.target as HTMLInputElement;
    const limpio = input.value.replace(/\D/g, '').slice(0, 6);
    if (input.value !== limpio) {
      input.value = limpio;
    }
    this.verificacionControls['codigo'].setValue(limpio);
  }

  private actualizarValidacionDocumento(tipo: string | null): void {
    const largo = tipo === 'CE' ? 9 : 8;
    const control = this.datosPersonalesControls['numeroDocumento'];
    control.setValidators([
      Validators.required,
      Validators.pattern(new RegExp(`^\\d{${largo}}$`))
    ]);
    control.setValue((control.value || '').slice(0, largo));
    control.updateValueAndValidity();
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
    // Si el usuario abandona la pestaña de registro, se descarta el borrador
    // guardado para no reabrir el modal en un registro a medias.
    if (mode !== 'registro' && this.mode === 'registro') {
      this.limpiarProgreso();
    }

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
            } else if (user && user.rol === 'asistente') {
              this.router.navigate(['/dashboard/asistente']);
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

  /**
   * Paso 1 del registro: valida email, contraseña y confirmación en local y
   * solicita al backend el envío de un código de 6 dígitos al correo. El
   * backend valida la disponibilidad del correo antes de enviarlo.
   */
  continuarRegistro(): void {
    this.error = '';
    this.success = '';

    if (this.credencialesForm.invalid) {
      this.credencialesForm.markAllAsTouched();
      return;
    }

    const email = this.credencialesControls['email'].value;
    this.enviandoCodigo = true;

    // reenviar = false: si ya hay un código vigente, el backend lo reutiliza
    // y no manda otro correo (por ejemplo, tras recargar la página).
    this.authService.solicitarCodigo(email, false).subscribe({
      next: (response) => {
        this.enviandoCodigo = false;
        this.registroStep = 2;
        this.success = response.enviado
          ? `Enviamos un código de 6 dígitos a ${email}. Revisa tu correo.`
          : `Ya te enviamos un código a ${email}. Usa el que llegó a tu correo.`;
        this.codigoEnviadoEn = Date.now();
        this.iniciarCooldown(60);
        this.guardarProgreso();
      },
      error: (response) => {
        this.enviandoCodigo = false;
        const mensaje = response.error?.error || 'No se pudo enviar el código. Inténtalo de nuevo.';
        this.error = mensaje;
        if (mensaje.toLowerCase().includes('registrado')) {
          this.credencialesControls['email'].markAsTouched();
        }
      }
    });
  }

  /**
   * Paso 2: valida los datos personales en local y avanza al paso 3.
   * No llama al backend: la cuenta se crea recién al confirmar el código.
   */
  continuarDatos(): void {
    this.error = '';
    this.success = '';

    if (this.datosPersonalesForm.invalid) {
      this.datosPersonalesForm.markAllAsTouched();
      return;
    }

    this.registroStep = 3;
    this.guardarProgreso();
  }

  /** Reenvía el código de verificación respetando el cooldown del backend. */
  reenviarCodigo(): void {
    if (this.reenvioCooldown > 0 || this.enviandoCodigo || this.loading) {
      return;
    }

    const email = this.credencialesControls['email'].value;
    this.error = '';
    this.success = '';
    this.enviandoCodigo = true;

    // reenviar = true: el usuario pidió explícitamente un código nuevo.
    this.authService.solicitarCodigo(email, true).subscribe({
      next: (response) => {
        this.enviandoCodigo = false;
        this.success = response.enviado
          ? `Enviamos un nuevo código a ${email}.`
          : `Ya hay un código vigente para ${email}. Revisa tu correo.`;
        this.codigoEnviadoEn = Date.now();
        this.iniciarCooldown(60);
        this.guardarProgreso();
      },
      error: (response) => {
        this.enviandoCodigo = false;
        const mensaje = response.error?.error || 'No se pudo reenviar el código.';
        this.error = mensaje;

        // El backend puede rechazar por cooldown: se refleja en el contador.
        const segundos = this.extraerSegundosEspera(mensaje);
        if (segundos !== null) {
          this.iniciarCooldown(segundos);
        }
      }
    });
  }

  /** Regresa del paso 2 al paso 1 conservando lo ya escrito. */
  volverACredenciales(): void {
    this.error = '';
    this.success = '';
    this.verificacionForm.reset();
    this.registroStep = 1;
    this.guardarProgreso();
  }

  /** Regresa del paso 3 al paso 2 conservando los datos ya escritos. */
  volverADatos(): void {
    this.error = '';
    this.success = '';
    this.registroStep = 2;
    this.guardarProgreso();
  }

  /** Paso 3: valida el código y crea la cuenta en el backend. */
  confirmarRegistro(): void {
    this.error = '';
    this.success = '';

    if (this.verificacionForm.invalid) {
      this.verificacionForm.markAllAsTouched();
      return;
    }

    this.loading = true;
    this.authService.registro(
      this.credencialesControls['email'].value,
      this.credencialesControls['password'].value,
      this.datosPersonalesControls['nombre'].value,
      this.datosPersonalesControls['apellido'].value,
      this.datosPersonalesControls['telefono'].value,
      this.datosPersonalesControls['tipoDocumento'].value,
      this.datosPersonalesControls['numeroDocumento'].value,
      this.datosPersonalesControls['aceptaTerminos'].value === true,
      this.verificacionControls['codigo'].value
    ).subscribe({
      next: () => {
        this.loading = false;
        this.resetRegistro();
        // setMode limpia los mensajes, por eso el éxito se asigna después
        this.setMode('login');
        this.success = 'Correo confirmado. Ya puedes iniciar sesión.';
      },
      error: (response) => {
        const mensaje = response.error?.error || 'Error al registrar usuario';
        this.error = mensaje;
        this.loading = false;
        // Si otro usuario tomó el correo mientras se completaba el formulario,
        // volvemos al paso 1 para que pueda cambiarlo.
        if (mensaje.toLowerCase().includes('email')) {
          this.registroStep = 1;
        }
      }
    });
  }

  /** Limpia los formularios del registro, el progreso guardado y vuelve al paso 1. */
  private resetRegistro(): void {
    this.credencialesForm.reset();
    this.datosPersonalesForm.reset({ tipoDocumento: 'DNI', aceptaTerminos: false });
    this.verificacionForm.reset();
    this.registroStep = 1;
    this.enviandoCodigo = false;
    this.codigoEnviadoEn = 0;
    this.detenerCooldown();
    this.limpiarProgreso();
    this.showRegisterPassword = false;
    this.showRegisterConfirmPassword = false;
  }

  /** Inicia el contador visual de reenvío (el backend también lo valida). */
  private iniciarCooldown(segundos: number): void {
    this.detenerCooldown();
    this.reenvioCooldown = segundos;
    this.cooldownInterval = setInterval(() => {
      this.reenvioCooldown = Math.max(0, this.reenvioCooldown - 1);
      if (this.reenvioCooldown === 0) {
        this.detenerCooldown();
      }
    }, 1000);
  }

  /** Detiene y reinicia el contador de reenvío. */
  private detenerCooldown(): void {
    if (this.cooldownInterval) {
      clearInterval(this.cooldownInterval);
      this.cooldownInterval = undefined;
    }
    this.reenvioCooldown = 0;
  }

  /** Extrae los segundos de un mensaje del tipo "Espera 42 segundos...". */
  private extraerSegundosEspera(mensaje: string): number | null {
    const coincidencia = /espera\s+(\d+)\s+segundos/i.exec(mensaje);
    return coincidencia ? Number(coincidencia[1]) : null;
  }

  /**
   * Acceso seguro a sessionStorage: en SSR (o si el navegador lo bloquea)
   * devuelve null y la persistencia simplemente se omite.
   */
  private obtenerSessionStorage(): Storage | null {
    if (typeof window === 'undefined') {
      return null;
    }
    try {
      return window.sessionStorage;
    } catch {
      return null;
    }
  }

  /**
   * Guarda en sessionStorage el avance del registro (solo si hay un email
   * escrito en modo registro). Así, recargar la página no obliga a empezar
   * de cero ni a pedir un código nuevo.
   */
  private guardarProgreso(): void {
    if (this.mode !== 'registro') {
      return;
    }

    const email = this.credencialesControls['email'].value;
    if (!email) {
      return;
    }

    const storage = this.obtenerSessionStorage();
    if (!storage) {
      return;
    }

    const progreso: ProgresoRegistro = {
      email,
      password: this.credencialesControls['password'].value || '',
      confirmPassword: this.credencialesControls['confirmPassword'].value || '',
      nombre: this.datosPersonalesControls['nombre'].value || '',
      apellido: this.datosPersonalesControls['apellido'].value || '',
      tipoDocumento: this.datosPersonalesControls['tipoDocumento'].value || 'DNI',
      numeroDocumento: this.datosPersonalesControls['numeroDocumento'].value || '',
      telefono: this.datosPersonalesControls['telefono'].value || '',
      aceptaTerminos: this.datosPersonalesControls['aceptaTerminos'].value === true,
      paso: this.registroStep,
      codigoEnviadoEn: this.codigoEnviadoEn || undefined,
      guardadoEn: Date.now()
    };

    try {
      storage.setItem(AuthModalComponent.CLAVE_PROGRESO, JSON.stringify(progreso));
    } catch {
      // Si el almacenamiento está lleno o bloqueado, se ignora: es solo una ayuda.
    }
  }

  /** Elimina el progreso guardado del registro. */
  private limpiarProgreso(): void {
    const storage = this.obtenerSessionStorage();
    if (!storage) {
      return;
    }
    try {
      storage.removeItem(AuthModalComponent.CLAVE_PROGRESO);
    } catch {
      // Sin acceso al almacenamiento no hay nada que limpiar.
    }
  }

  /**
   * Retoma un registro a medias tras una recarga: restaura los datos y reabre
   * el modal en el paso donde se quedó el usuario.
   *
   * Se descarta el progreso vencido (más de 30 min), inválido o de un usuario
   * que ya inició sesión.
   */
  private restaurarProgreso(): void {
    const storage = this.obtenerSessionStorage();
    if (!storage) {
      return;
    }

    if (this.authService.isAuthenticated()) {
      this.limpiarProgreso();
      return;
    }

    let progreso: ProgresoRegistro | null = null;
    try {
      const crudo = storage.getItem(AuthModalComponent.CLAVE_PROGRESO);
      progreso = crudo ? (JSON.parse(crudo) as ProgresoRegistro) : null;
    } catch {
      progreso = null;
    }

    if (!progreso || !progreso.email) {
      this.limpiarProgreso();
      return;
    }

    if (!progreso.guardadoEn
      || Date.now() - progreso.guardadoEn > AuthModalComponent.PROGRESO_TTL_MS) {
      this.limpiarProgreso();
      return;
    }

    this.credencialesForm.setValue({
      email: progreso.email,
      password: progreso.password || '',
      confirmPassword: progreso.confirmPassword || progreso.password || ''
    });

    this.datosPersonalesForm.setValue({
      nombre: progreso.nombre || '',
      apellido: progreso.apellido || '',
      tipoDocumento: progreso.tipoDocumento || 'DNI',
      numeroDocumento: progreso.numeroDocumento || '',
      telefono: progreso.telefono || '',
      aceptaTerminos: progreso.aceptaTerminos === true
    });

    this.registroStep = progreso.paso === 3 ? 3 : progreso.paso === 2 ? 2 : 1;
    this.codigoEnviadoEn = progreso.codigoEnviadoEn || 0;
    this.success = 'Recuperamos tu registro. Continúa donde lo dejaste.';

    // Si el código se envió hace menos de 60 s, se respeta el tiempo restante.
    if (this.codigoEnviadoEn) {
      const transcurrido = Math.floor((Date.now() - this.codigoEnviadoEn) / 1000);
      const restante = 60 - transcurrido;
      if (restante > 0) {
        this.iniciarCooldown(restante);
      }
    }

    this.authModalService.open('registro');
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
