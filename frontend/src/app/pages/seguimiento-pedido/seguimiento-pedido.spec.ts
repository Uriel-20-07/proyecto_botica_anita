import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { SeguimientoPedidoComponent } from './seguimiento-pedido';
import { PedidoService } from '../../services/pedido.service';

describe('SeguimientoPedidoComponent', () => {
  let component: SeguimientoPedidoComponent;
  let fixture: ComponentFixture<SeguimientoPedidoComponent>;
  let mockPedidoService: jasmine.SpyObj<PedidoService>;

  beforeEach(async () => {
    mockPedidoService = jasmine.createSpyObj('PedidoService', ['obtenerPedidos']);
    mockPedidoService.obtenerPedidos.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [SeguimientoPedidoComponent],
      providers: [
        provideHttpClient(),
        provideRouter([]),
        { provide: PedidoService, useValue: mockPedidoService }
      ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(SeguimientoPedidoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
