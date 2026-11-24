package com.example.demo.repositories;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.example.demo.dto.chatbot.ProductoChatDTO;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

/**
 * Búsqueda de productos por texto libre para el chatbot.
 *
 * A diferencia de ChatbotCatalogoRepository (consultas fijas), aquí la cantidad de términos varía en cada
 * consulta, por lo que el JPQL se arma en tiempo de ejecución. Los valores del cliente NUNCA se concatenan al
 * texto de la consulta: solo se agregan parámetros con nombre (:t0, :t1...), así que no hay inyección.
 *
 * Qué hace la búsqueda:
 * - Busca en nombre + descripción + categoría + subcategoría, sin importar mayúsculas ni tildes.
 * - Basta que coincida UNO de los términos; se muestran primero los productos que coinciden con más términos.
 * - Permite excluir productos (los que ya se mostraron).
 *
 * No es @Transactional a propósito: si una consulta fallara, el servicio puede usar la búsqueda simple de
 * respaldo sin que la transacción quede marcada para deshacerse.
 */
@Repository
public class ChatbotBusquedaRepository {

    private static final int SIN_FILTRO = ChatbotCatalogoRepository.SIN_FILTRO;

    private static final String SELECT_DTO = "select new com.example.demo.dto.chatbot.ProductoChatDTO("
            + "p.idProducto, p.nombre, p.precioVenta, p.precioOferta, p.enOferta, "
            + "(p.stock - coalesce(p.stockEnCarrito, 0)), p.fechaCaducidad, p.imgUrl) ";

    /** Texto donde se busca, en minúsculas y sin tildes (la "ñ" pasa a "n"). */
    private static final String TEXTO_BUSCABLE = sinAcentos(
            "concat(coalesce(p.nombre, ''), ' ', coalesce(p.descripcion, ''), ' ', "
                    + "coalesce(c.nombre, ''), ' ', coalesce(s.nombre, ''))");

    @PersistenceContext
    private EntityManager em;

    /**
     * Productos que coinciden con alguno de los patrones y cumplen los filtros.
     *
     * @param patrones  patrones LIKE ya normalizados (ver TextoChat.patrones). Vacío = sin condición de texto.
     * @param excluir   ids de productos que no deben aparecer.
     * @param ofertas   true = solo productos con oferta vigente.
     */
    public List<ProductoChatDTO> buscar(List<String> patrones, int idCategoria, int idSubcategoria, int idMarca,
                                        boolean ofertas, Collection<Integer> excluir, LocalDate hoy, int limite) {
        StringBuilder q = new StringBuilder(SELECT_DTO)
                .append("from Producto p left join p.categoria c left join p.subcategoria s ")
                .append("where p.idProducto not in (:excluir)");

        if (idCategoria != SIN_FILTRO) {
            q.append(" and p.categoria.idCategoria = :idCategoria");
        }
        if (idSubcategoria != SIN_FILTRO) {
            q.append(" and p.subcategoria.idSubcategoria = :idSubcategoria");
        }
        if (idMarca != SIN_FILTRO) {
            q.append(" and p.marca.idMarca = :idMarca");
        }
        if (ofertas) {
            q.append(" and ").append(ChatbotCatalogoRepository.EN_OFERTA);
        }
        if (!patrones.isEmpty()) {
            q.append(" and (");
            for (int i = 0; i < patrones.size(); i++) {
                q.append(i > 0 ? " or " : "").append(TEXTO_BUSCABLE).append(" like :t").append(i);
            }
            q.append(")");
        }

        // Orden: disponibles primero; luego los que coinciden con más términos; luego ofertas; luego alfabético
        q.append(" order by case when ").append(ChatbotCatalogoRepository.DISPONIBLE).append(" then 0 else 1 end");
        if (!patrones.isEmpty()) {
            q.append(", (");
            for (int i = 0; i < patrones.size(); i++) {
                q.append(i > 0 ? " + " : "")
                        .append("case when ").append(TEXTO_BUSCABLE).append(" like :t").append(i)
                        .append(" then 1 else 0 end");
            }
            q.append(") desc");
        }
        q.append(", case when ").append(ChatbotCatalogoRepository.EN_OFERTA).append(" then 0 else 1 end, p.nombre");

        TypedQuery<ProductoChatDTO> query = em.createQuery(q.toString(), ProductoChatDTO.class);
        query.setParameter("excluir", listaNoVacia(excluir));
        query.setParameter("hoy", hoy);
        if (idCategoria != SIN_FILTRO) {
            query.setParameter("idCategoria", idCategoria);
        }
        if (idSubcategoria != SIN_FILTRO) {
            query.setParameter("idSubcategoria", idSubcategoria);
        }
        if (idMarca != SIN_FILTRO) {
            query.setParameter("idMarca", idMarca);
        }
        for (int i = 0; i < patrones.size(); i++) {
            query.setParameter("t" + i, patrones.get(i));
        }
        query.setMaxResults(limite);
        return query.getResultList();
    }

    /**
     * Productos disponibles de la misma subcategoría que los ya mostrados, sin repetir ninguno de ellos.
     *
     * Se limita a la subcategoría (y no a la categoría completa) a propósito: en una botica una categoría
     * amplia mezcla medicamentos para cosas muy distintas, y sugerir un producto fuera de su línea no es seguro.
     */
    public List<ProductoChatDTO> similares(Collection<Integer> mostrados, LocalDate hoy, int limite) {
        String q = SELECT_DTO + "from Producto p "
                + "where p.idProducto not in (:mostrados) and " + ChatbotCatalogoRepository.DISPONIBLE + " "
                + "and p.subcategoria.idSubcategoria in ("
                + "select m.subcategoria.idSubcategoria from Producto m "
                + "where m.idProducto in (:mostrados) and m.subcategoria is not null) "
                + "order by case when " + ChatbotCatalogoRepository.EN_OFERTA + " then 0 else 1 end, p.nombre";

        TypedQuery<ProductoChatDTO> query = em.createQuery(q, ProductoChatDTO.class);
        query.setParameter("mostrados", listaNoVacia(mostrados));
        query.setParameter("hoy", hoy);
        query.setMaxResults(limite);
        return query.getResultList();
    }

    /** Una lista vacía genera "in ()", que es inválido en SQL: se agrega un id que no existe. */
    private static List<Integer> listaNoVacia(Collection<Integer> ids) {
        List<Integer> lista = new ArrayList<>(ids == null ? List.of() : ids);
        if (lista.isEmpty()) {
            lista.add(-1);
        }
        return lista;
    }

    private static String sinAcentos(String expresion) {
        String resultado = "lower(" + expresion + ")";
        String[][] reemplazos = {{"á", "a"}, {"é", "e"}, {"í", "i"}, {"ó", "o"}, {"ú", "u"}, {"ü", "u"}, {"ñ", "n"}};
        for (String[] r : reemplazos) {
            resultado = "replace(" + resultado + ", '" + r[0] + "', '" + r[1] + "')";
        }
        return resultado;
    }
}
