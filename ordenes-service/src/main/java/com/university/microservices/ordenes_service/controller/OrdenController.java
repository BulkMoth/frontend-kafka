package com.university.microservices.ordenes_service.controller;

import com.university.microservices.ordenes_service.model.Orden;
import com.university.microservices.ordenes_service.repository.OrdenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ordenes")
public class OrdenController {

    @Autowired
    private OrdenRepository ordenRepository;

    @Autowired
    private com.university.microservices.ordenes_service.repository.EnvioRepository envioRepository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    private final org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();

    @PostMapping
    public ResponseEntity<?> createOrden(@RequestBody Orden orden) {
        try {
            if (orden.getProductosIds() == null || orden.getProductosIds().isEmpty()) {
                return ResponseEntity.badRequest().body("La orden debe contener productos.");
            }

            // Simulamos un fallo ocasional si el total es negativo para probar el broker-message-be
            if (orden.getTotal() != null && orden.getTotal().doubleValue() < 0) {
                throw new RuntimeException("Total invalido. Simulando fallo para Retry Job.");
            }

            // Validar stock disponible
            for (String productoId : orden.getProductosIds()) {
                try {
                    String url = "http://productos-service:8081/productos/" + productoId;
                    Map<?, ?> producto = restTemplate.getForObject(url, Map.class);
                    if (producto == null) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Producto no encontrado: " + productoId);
                    }
                    Integer stock = (Integer) producto.get("quantity");
                    if (stock == null || stock <= 0) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("No hay stock suficiente para el producto: " + productoId);
                    }
                } catch (Exception e) {
                    // Si falla el servicio de productos por algo que no sea 404, lanzamos error para retry
                    if (e.getMessage().contains("404")) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Producto no encontrado: " + productoId);
                    }
                    throw new RuntimeException("Error validando stock (servicio productos no disponible): " + e.getMessage());
                }
            }

            orden.setStatus("CREATED");
            Orden saved = ordenRepository.save(orden);
            
            // Flujo 2: Emitir evento de actualización de inventario
            try {
                kafkaTemplate.send("inventory_update_events", saved.getId(), new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(saved));
            } catch (Exception e) {
                log.error("Error enviando evento de inventario", e);
            }

            return new ResponseEntity<>(saved, HttpStatus.CREATED);
        } catch (Exception e) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("data", orden);
            payload.put("action", "CREATE_ORDER");
            payload.put("error", e.getMessage());
            try {
                kafkaTemplate.send("order_retry_jobs", orden.getId() != null ? orden.getId() : "NEW", new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload));
            } catch (Exception jsonEx) {}
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error, enviado a reintento: " + e.getMessage());
        }
    }

    @GetMapping
    public List<Orden> getAllOrdenes() {
        return ordenRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Orden> getOrdenById(@PathVariable String id) {
        return ordenRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/producto/{productoId}")
    public ResponseEntity<List<Orden>> getOrdenesByProducto(@PathVariable String productoId) {
        return ResponseEntity.ok(ordenRepository.findByProductosIdsContaining(productoId));
    }

    @GetMapping("/envios")
    public ResponseEntity<List<com.university.microservices.ordenes_service.model.Envio>> getAllEnvios() {
        return ResponseEntity.ok(envioRepository.findAll());
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<Orden>> getOrdenesByUsuario(@PathVariable String usuarioId) {
        return ResponseEntity.ok(ordenRepository.findByUsuarioId(usuarioId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateOrden(@PathVariable String id, @RequestBody Orden updatedOrden) {
        try {
            if (!ordenRepository.existsById(id)) {
                return ResponseEntity.notFound().build();
            }
            
            // Simulamos fallo para retry
            if (updatedOrden.getTotal() != null && updatedOrden.getTotal().doubleValue() < 0) {
                throw new RuntimeException("Total invalido en actualización. Simulando fallo para Retry Job.");
            }

            return ordenRepository.findById(id).map(existingOrden -> {
                boolean productsChanged = !existingOrden.getProductosIds().equals(updatedOrden.getProductosIds());
                
                existingOrden.setProductosIds(updatedOrden.getProductosIds());
                existingOrden.setTotal(updatedOrden.getTotal());
                existingOrden.setStatus(updatedOrden.getStatus());
                Orden saved = ordenRepository.save(existingOrden);

                // Flujo 2: Si cambian productos, emitir evento
                if (productsChanged) {
                    try {
                        kafkaTemplate.send("inventory_update_events", id, new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(saved));
                    } catch (Exception e) {}
                }
                return ResponseEntity.ok(saved);
            }).orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("data", updatedOrden);
            payload.put("action", "UPDATE_ORDER");
            payload.put("error", e.getMessage());
            try {
                kafkaTemplate.send("order_retry_jobs", id, new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload));
            } catch (Exception jsonEx) {}
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error actualizando orden, enviado a reintento: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteOrden(@PathVariable String id) {
        if (!ordenRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        // Validar si la orden tiene pagos
        try {
            String url = "http://pagos-service:8083/pagos/orden/" + id;
            List<?> pagos = restTemplate.getForObject(url, List.class);
            if (pagos != null && !pagos.isEmpty()) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("No se puede eliminar la orden porque tiene pagos asociados.");
            }
        } catch (Exception e) {
            // Log error
        }

        ordenRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateOrdenStatus(@PathVariable String id, @RequestParam String status) {
        try {
            return ordenRepository.findById(id).map(orden -> {
                orden.setStatus(status);
                Orden saved = ordenRepository.save(orden);

                // Flujo 1: Emitir evento de cambio de estatus
                try {
                    kafkaTemplate.send("order_status_changed_events", id, new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(saved));
                } catch (Exception e) {}

                return ResponseEntity.ok(saved);
            }).orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("id", id);
            payload.put("status", status);
            payload.put("action", "UPDATE_ORDER_STATUS");
            payload.put("error", e.getMessage());
            try {
                kafkaTemplate.send("order_retry_jobs", id, new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload));
            } catch (Exception jsonEx) {}
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error, enviado a reintento: " + e.getMessage());
        }
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OrdenController.class);
}
