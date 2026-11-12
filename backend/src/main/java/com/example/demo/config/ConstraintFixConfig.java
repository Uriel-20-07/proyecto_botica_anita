package com.example.demo.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ConstraintFixConfig implements CommandLineRunner {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) {
        try {
            entityManager.createNativeQuery("ALTER TABLE pedidos DROP CONSTRAINT IF EXISTS pedidos_metodo_pago_check").executeUpdate();
            entityManager.createNativeQuery("ALTER TABLE pedidos ADD CONSTRAINT pedidos_metodo_pago_check CHECK (metodo_pago IN ('TARJETA','YAPE','PLIN','SIP','EFECTIVO'))").executeUpdate();
            System.out.println("Constraint metodo_pago actualizado OK.");
        } catch (Exception e) {
            System.out.println("No se pudo actualizar constraint metodo_pago: " + e.getMessage());
        }
    }
}
