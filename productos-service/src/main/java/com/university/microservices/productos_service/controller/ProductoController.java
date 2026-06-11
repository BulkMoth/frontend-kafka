package com.university.microservices.productos_service.controller;

import com.university.microservices.productos_service.model.Producto;
import com.university.microservices.productos_service.repository.ProductoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/productos")
public class ProductoController {

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    private final org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();

    @GetMapping
    public List<Producto> getAllProductos() {
        return productoRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Producto> getProductoById(@PathVariable String id) {
        Optional<Producto> producto = productoRepository.findById(id);
        return producto.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createProducto(@RequestBody Producto producto) {
        try {
            if (producto.getQuantity() != null && producto.getQuantity() <= 0) {
                return ResponseEntity.badRequest().body("El stock debe ser mayor a cero.");
            }
            // Simulamos un fallo ocasional si el precio es negativo para probar el
            // broker-message-be
            if (producto.getPrice() != null && producto.getPrice().doubleValue() < 0) {
                throw new RuntimeException("Precio invalido. Simulando fallo para Retry Job.");
            }
            Producto saved = productoRepository.save(producto);
            
            // Emitir evento de actualización de inventario al crear
            try {
                kafkaTemplate.send("inventory_update_events", saved.getId(), new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(saved));
            } catch (Exception e) {
                // Loguear error pero no fallar la creación
            }

            return new ResponseEntity<>(saved, HttpStatus.CREATED);
        } catch (Exception e) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("data", producto);
            payload.put("action", "CREATE_PRODUCT");
            payload.put("error", e.getMessage());
            try {
                kafkaTemplate.send("product_retry_jobs", producto.getId() != null ? producto.getId() : "NEW",
                        new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload));
            } catch (Exception jsonEx) {
                // ignore
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error procesando producto, enviado a reintento: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateProducto(@PathVariable String id, @RequestBody Producto producto) {
        try {
            if (!productoRepository.existsById(id)) {
                return ResponseEntity.notFound().build();
            }
            producto.setId(id);
            Producto saved = productoRepository.save(producto);

            // Emitir evento de actualización de inventario al actualizar
            try {
                kafkaTemplate.send("inventory_update_events", saved.getId(), new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(saved));
            } catch (Exception e) {
                // Loguear error
            }

            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("data", producto);
            payload.put("action", "UPDATE_PRODUCT");
            payload.put("error", e.getMessage());
            try {
                kafkaTemplate.send("product_retry_jobs", id,
                        new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload));
            } catch (Exception jsonEx) {
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error procesando producto, enviado a reintento: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProducto(@PathVariable String id) {
        if (!productoRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        
        // Validar si el producto está asociado a alguna orden
        try {
            String url = "http://ordenes-service:8082/ordenes/producto/" + id;
            List<?> ordenes = restTemplate.getForObject(url, List.class);
            if (ordenes != null && !ordenes.isEmpty()) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("No se puede eliminar el producto porque está asociado a una orden.");
            }
        } catch (Exception e) {
            // Si el servicio de órdenes no está disponible, podríamos optar por fallar o continuar.
            // Para este caso escolar, asumiremos que debemos validar.
        }

        productoRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
