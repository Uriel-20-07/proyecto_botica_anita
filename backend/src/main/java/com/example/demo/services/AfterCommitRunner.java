package com.example.demo.services;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Utilidad para diferir tareas (por ejemplo, disparar un correo asincrono)
 * hasta que la transaccion actual haya hecho commit. Si no hay una
 * transaccion activa, la tarea se ejecuta de inmediato.
 */
public final class AfterCommitRunner {

    private AfterCommitRunner() {
    }

    public static void run(Runnable tarea) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    tarea.run();
                }
            });
        } else {
            tarea.run();
        }
    }
}
