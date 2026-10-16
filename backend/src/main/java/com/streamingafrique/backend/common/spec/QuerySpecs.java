package com.streamingafrique.backend.common.spec;

import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

/**
 * Fabrique de {@link Specification} réutilisable pour TOUS les endpoints
 * de liste (lives, vidéos, créateurs, catégories, clips).
 *
 * But : centraliser les motifs de filtrage/recherche qui reviennent partout
 * — recherche insensible à la casse sur un champ texte, égalité sur un chemin
 * de propriété (y compris relation imbriquée), filtre par slug d'une
 * relation. La combinaison de plusieurs critères reste à la charge du
 * service (Specification AND).
 *
 * Tous les prédicats sont "NO-OP" (conjonction vraie) dès que l'argument
 * est vide : appeler findAll(spec, pageable) avec une liste vide de
 * critères renvoie la page non filtrée, sans branche if/else dans les
 * services et sans NULL à gérer par Spring Data.
 *
 * ⚠️ La recherche s'exécute TOUJOURS en base (LOWER + LIKE) : elle ne
 * charge jamais plus que la page demandée (OFFSET/LIMIT côté SQL).
 */
public final class QuerySpecs {

    /** Caractère d'échappement utilisé dans les LIKE — cohérent dans le LIKE et le pattern. */
    public static final char LIKE_ESCAPE = '\\';

    private QuerySpecs() {
    }

    /**
     * Recherche « contient » insensible à la casse sur un champ texte.
     * Les wildcards LIKE (% et _) saisis par l'utilisateur sont échappés :
     * une recherche "100%" ne matche que la chaîne littérale « 100% ».
     *
     * @param field nom de la propriété de l'entité racine
     * @param term  terme recherché (peut être null/blank → aucun filtre)
     */
    public static <T> Specification<T> textContains(String field, String term) {
        return (root, query, cb) -> {
            if (term == null || term.isBlank()) {
                return cb.conjunction();
            }
            return cb.like(cb.lower(root.get(field).as(String.class)),
                    likePattern(term), LIKE_ESCAPE);
        };
    }

    /**
     * Recherche « contient » insensible à la casse sur PLUSIEURS champs,
     * combinés en OU : « un des champs matche ». Idéal pour la recherche
     * globale (« search ») : un objet est trouvé si le terme apparaît dans
     * n'importe lequel de ses champs texte.
     *
     * @param fields noms des propriétés texte de l'entité racine
     * @param term   terme recherché (peut être null/blank → aucun filtre)
     */
    public static <T> Specification<T> textContainsAny(List<String> fields, String term) {
        return (root, query, cb) -> {
            if (term == null || term.isBlank()) {
                return cb.conjunction();
            }
            Predicate[] predicates = fields.stream()
                    .map(field -> (Predicate) cb.like(cb.lower(root.get(field).as(String.class)),
                            likePattern(term), LIKE_ESCAPE))
                    .toArray(Predicate[]::new);
            return cb.or(predicates);
        };
    }

    /**
     * Égalité sur un chemin de propriété, y compris navigateur de relation
     * ("creator.id", "category.id"...). Aucun résultat si la valeur est nulle.
     */
    public static <T> Specification<T> pathEquals(String path, Object value) {
        return (root, query, cb) -> {
            if (value == null) {
                return cb.conjunction();
            }
            return cb.equal(path(root, path), value);
        };
    }

    /**
     * Égalité insensible à la casse sur une propriété d'une entité liée.
     * Utilise un INNER JOIN sur la relation : filtrer "category=gaming"
     * exclut naturellement les lignes sans catégorie.
     */
    public static <T> Specification<T> relationFieldEquals(String relation, String field, String value) {
        return (root, query, cb) -> {
            if (value == null || value.isBlank()) {
                return cb.conjunction();
            }
            Join<Object, Object> join = root.join(relation, JoinType.INNER);
            return cb.equal(cb.lower(join.get(field).as(String.class)),
                    value.toLowerCase(Locale.ROOT));
        };
    }

    /**
     * Combine une liste de critères avec ET (« AND »).
     * Une liste vide renvoie une specification sans filtre (tout).
     */
    public static <T> Specification<T> and(List<Specification<T>> criteria) {
        Specification<T> combined = null;
        for (Specification<T> criterion : criteria) {
            combined = (combined == null) ? criterion : combined.and(criterion);
        }
        return combined;
    }

    /** Convertit un terme libre en pattern LIKE : %terme%, wildcards échappés, minuscules. */
    public static String likePattern(String term) {
        String escaped = term
                .replace("" + LIKE_ESCAPE, "" + LIKE_ESCAPE + LIKE_ESCAPE)
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
        return "%" + escaped.toLowerCase(Locale.ROOT) + "%";
    }

    /** Navigue un chemin "a.b.c" jusqu'à la propriété cible. */
    private static Path<Object> path(Path<?> root, String path) {
        Path<Object> current = (Path<Object>) root;
        for (String part : path.split("\\.")) {
            current = current.get(part);
        }
        return current;
    }
}