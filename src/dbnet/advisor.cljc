(ns dbnet.advisor
  "DatabaseNetworkProfessionalsAdvisor — proposes a schema-migration
  operation (propose a migration, apply a migration) for a registered
  organization. Swappable mock/llm; the advisor ONLY proposes —
  `dbnet.governor` checks 3NF against the registered candidate keys
  independently. Modeled on cloud-itonami-isco-4311's advisor.

  A proposal: {:op :propose-migration|:apply-migration
               :effect :propose :schema-id str
               :fds [{:determinant #{} :dependent #{}}]
               :stake kw :confidence n :rationale str}")

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake schema-id fds] :as request}]
  {:op op
   :effect :propose
   :schema-id schema-id
   :fds fds
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for client " (:client-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a database schema advisor. Given a request, propose an :op,
   the :schema-id and :fds (functional dependencies), an honest
   :confidence and a :stake. Never introduce a transitive dependency —
   the governor checks every FD against the registered candidate keys.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
