(ns bluegenes.pages.templates.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :as re-frame :refer [reg-sub]]
            [bluegenes.pages.templates.helpers :as template-helpers]
            [bluegenes.utils :refer [parse-template-rank]]
            [clojure.string :as str]))

(defn extract-tag-categories [tags]
  (->> tags
       (keep #(second (re-matches #"im:aspect:([^\s]+)" %)))
       (str/join " ")))

(defn template-contains-string? [s [_ template]]
  (if (empty? s)
    true
    (let [ss (map str/lower-case (-> s str/trim (str/split #"\s+")))
          {:keys [title description tags]} template
          all-text (->> (extract-tag-categories tags)
                        (str title " " description " ")
                        (str/lower-case))]
      (every? #(str/includes? all-text %) ss))))

(reg-sub
 :templates
 (fn [db _]
   (get-in db [:assets :templates (:current-mine db)])))

(reg-sub
 :template-chooser/count
 (fn [db]
   (get-in db [:components :template-chooser :count])))

(reg-sub
 :template-chooser/results-preview
 (fn [db]
   (get-in db [:components :template-chooser :results-preview])))

(reg-sub
 :template-chooser/preview-error
 (fn [db]
   (get-in db [:components :template-chooser :preview-error])))

(reg-sub
 :template-chooser/counting?
 (fn [db]
   (get-in db [:components :template-chooser :counting?])))

(reg-sub
 :template-chooser/fetching-preview?
 (fn [db]
   (get-in db [:components :template-chooser :fetching-preview?])))

(reg-sub
 :template-chooser/text-filter
 (fn [db]
   (get-in db [:components :template-chooser :text-filter])))

(reg-sub
 :template-chooser-categories
 :<- [:templates]
 (fn [templates]
   (template-helpers/categories templates)))

(reg-sub
 :templates-by-rank
 :<- [:templates]
 (fn [templates]
   (sort-by (comp parse-template-rank :rank val) < templates)))

(reg-sub
 :templates-by-category
 :<- [:templates-by-rank]
 :<- [:selected-template-category]
 :<- [:template-chooser/text-filter]
 (fn [[sorted-templates category text-filter]]
   (let [filter-pred (fn [tag category] (= tag (str "im:aspect:" category)))
         filter-fn
         (fn [[id details]]
           (if category
             (some? (some (fn [tag] (filter-pred tag category))
                          (:tags details)))
             true))]
     (->> sorted-templates
          (filter filter-fn)
          (filter (partial template-contains-string? text-filter))))))

(defn popular-templates
  "Takes a sequence of sorted templates (presumably by rank) and returns a map
  from categories (determined by the im:aspect:* tag) to the templates. The
  categories are the top 6 with the most numerous templates that are part of
  the top 50 ranked templates. The templates to each category are part of the
  same top 50 ranked templates, although reduced to the top 10 for each category."
  [sorted-templates]
  (let [top-50-templates (take-while #(<= (-> % val :rank parse-template-rank) 50)
                                     sorted-templates)
        top-6-categories (->> top-50-templates
                              (mapcat (comp :tags val))
                              (filter #(str/starts-with? % "im:aspect:"))
                              (frequencies)
                              (sort-by val >)
                              (take 6)
                              (keys))]
    (zipmap top-6-categories
            (map (fn [category-tag]
                   (take 10
                         (filter (comp #(contains? % category-tag) set :tags)
                                 (vals top-50-templates))))
                 top-6-categories))))

(reg-sub
 :templates-by-popularity
 :<- [:templates-by-rank]
 (fn [sorted-templates]
   (popular-templates sorted-templates)))

(reg-sub
 :templates-by-popularity/all-categories
 :<- [:templates-by-popularity]
 (fn [category->templates]
   (keys category->templates)))

(reg-sub
 :templates-by-popularity/category
 :<- [:templates-by-popularity]
 (fn [category->templates [_ category]]
   (get category->templates category)))

(reg-sub
 :template-chooser/model
 (fn [db _]
   (:model (:assets db))))

(reg-sub
 :selected-template
 (fn [db _]
   (get-in db [:components :template-chooser :selected-template])))

(reg-sub
 :selected-template-name
 (fn [db _]
   (get-in db [:components :template-chooser :selected-template-name])))

(reg-sub
 :selected-template-category
 (fn [db _]
   (get-in db [:components :template-chooser :selected-template-category])))

(reg-sub
 :selected-template-service
 (fn [db _]
   (get-in db [:components :template-chooser :selected-template-service])))

(reg-sub
 :template-chooser/web-service-url
 :<- [:selected-template]
 :<- [:active-service]
 (fn [[template service]]
   (template-helpers/web-service-url service template)))

(reg-sub
 :template-chooser/changed-selected?
 :<- [:selected-template]
 :<- [:selected-template-name]
 :<- [:templates]
 (fn [[selected-tmpl tmpl-name all-templates]]
   (not= selected-tmpl (get all-templates tmpl-name))))
