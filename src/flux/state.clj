(ns flux.state
  (:require [flux.utils :as utils]))

(def ^:dynamic *stack* (atom {}))

(defn ^:private new-node [level]
  {:children '()
   :input    nil
   :output   nil
   :throw    nil
   :schema   nil
   :level    level})

(defn ^:private new-child [var]
  {:name (utils/namespaced var)
   :var  var})

(defn clear-stack! []
  (reset! *stack* {}))

(defn stack []
  (deref *stack*))

(defn list-children [node]
  (-> (stack)
      node
      :children
      seq))

(defn flat-children []
  (->> (stack)
       vals
       (map :children)
       flatten
       (map :var)))

(defn sorted-stack []
  (sort-by (utils/val-fn #(:level %)) (stack)))

(defn find-level [level]
  (->> (stack)
       (filter (fn [[k v]] (= level (:level v))))))

(defn find-root []
  (key (first (find-level 0))))

(defn find-node [node]
  (get (stack) node))

(defn register-node! [node level]
  (swap! *stack* assoc-in [node] (new-node level)))

(defn register-child!
  [var node]
  (swap! *stack* update-in [node :children] conj (new-child var)))

(defn register-schema!
  [schema node]
  (swap! *stack* update node assoc :schema schema))

(defn register-input! [node args]
  (swap! *stack* update node assoc :input args))

(defn register-output! [node result]
  (swap! *stack* update node assoc :output result))

(defn register-exception! [node ex]
  (let [exception-data (ex-data ex)]
    (swap! *stack* update node assoc :throw exception-data)))

(defn ^:private expand-children [current filter-keys]
  (map (fn [{:keys [name] :as child}]
         (if-let [children (list-children name)]
           (-> child
               (merge (find-node name))
               (assoc :children children)
               (select-keys filter-keys)
               (update-in [:children] expand-children filter-keys))
           (-> child
               (merge (find-node name))
               (assoc :children [])
               (select-keys filter-keys))))
       current))

(defn sequential-stack
  ([] (sequential-stack {:filter-keys [:name :children :input :output]}))
  ([{:keys [filter-keys] :as _options}]
   (let [root (find-root)]
     (-> (update-in (stack) [root :children] expand-children filter-keys)
         (get root)
         (assoc :name root)
         (select-keys filter-keys)))))