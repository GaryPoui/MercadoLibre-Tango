package com.tuempresa.stocksync.model;

public enum Rol {
    ADMIN,      // Acceso total: CRUD, sync, análisis, gestión usuarios
    OPERATOR,   // Puede sincronizar, ver stock, mover stock
    READONLY    // Solo lectura: ver stock, métricas, alertas
}
