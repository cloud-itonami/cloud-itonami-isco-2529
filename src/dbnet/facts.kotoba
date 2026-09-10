(ns dbnet.facts
  "Well-formedness of the values the ISCO-08 2529 database and network
  professionals (NEC) actor governs: the client record, the registered
  candidate keys, and the proposal envelope.

  Runtime: portable `.cljc` (pure predicates, no host interop). Deliberately
  no `clojure.string` dependency — `blank?` is spelled out below so this
  namespace adds no coordinate to `deps.edn`.

  Why this namespace exists — four measurements on the pre-change tree, all
  against a registered client whose schema `orders` had the registered
  candidate key `#{\"order_id\"}`.

  1. A registered candidate key with no `:attrs` switched the whole 3NF check
     off:

         (register-key! s {:key-id \"k1\" :client-id \"c1\" :schema-id \"orders\"})
         (governor/check <propose-migration with a transitive FD> ...)
         => {:ok? true :violations []}

     `superkey?` asks `(set/subset? (:attrs k) determinant)`, and
     `clojure.set/subset?` of `nil` is TRUE for every set — the empty
     precondition is satisfied by everything. So one key registered without
     attributes makes every determinant a superkey, and the repo's headline
     invariant admits every transitive dependency in silence. A registered
     record that cannot be compared against is a defect in the registration,
     and is reported as one rather than quietly disabling the comparison.

  2. Client provenance was written as `(nil? client-record)`. That asks
     whether the store returned something, not whether that something
     identifies a client. Registering the empty map put a record under the key
     `nil`, after which a request carrying no `:client-id` resolved to it:

         (register-client! s {})
         (governor/check {} {} <clean migration proposal> s)
         => {:ok? true :violations []}

     `nil?` is a fact about the store's return value. Provenance is a fact
     about the record. Those are different questions, and the second one needs
     a place to live.

  3. `:confidence` is compared against the governor's floor to decide
     escalation, but nothing constrained it:

         <propose-migration, :confidence 99.0>   => {:ok? true}  ; not escalated
         <propose-migration, :confidence \"high\"> => ClassCastException

     A missing confidence already reads as 0.0 in the governor and therefore
     escalates, which is the safe direction and is left alone. A present but
     unusable confidence is the unsafe direction: it either buys the advisor
     out of escalation with a number that means nothing, or it crashes. And
     the crash is host-specific — `(< \"high\" 0.6)` throws on `:clj` but
     compiles to the JavaScript `\"high\" < 0.6`, which is `false`, so on
     `:cljs` the same tree ADMITS the proposal instead of throwing. Same
     defect, opposite outcomes per host.

  4. A malformed functional dependency crashed rather than refusing:

         <:fds [{:determinant 5 :dependent #{\"x\"}}]>
         => java.lang.IllegalArgumentException

     A crash is not a refusal. It fails the request, but in a shape no caller
     can audit, and it is again host-divergent. The FD set is the evidence a
     migration offers for its own normalization; an unreadable FD is a defect
     in the evidence.

  A fifth measurement is about absence rather than malformation: a migration
  declaring `:fds []` (or omitting `:fds` entirely) was admitted clean,
  because the 3NF check is a `keep` over the FD list and an empty list yields
  no violations. The invariant was vacuously satisfied. For a schema
  migration the declared FD set *is* the evidence that normalization was
  considered at all, so its absence is the violation rather than the reason
  not to check — the same reading this fleet applied to a link test's missing
  measurement.

  The invariant that binds all of these: **a check is never skipped
  silently.** Where a comparison cannot be made, the reason is recorded here
  as a violation of its own, so an unusable value produces a refusal rather
  than an admission."
  (:require [dbnet.operation :as op]))

(defn- blank?
  "True for nil, a non-string, or a string of only whitespace. Spelled out
  rather than pulled from `clojure.string` so this namespace stays
  dependency-free."
  [s]
  (or (not (string? s))
      (every? #(contains? #{\space \tab \newline \return} %) s)))

(defn attr-set?
  "True for a non-empty collection of non-blank attribute names. Sets, vectors
  and lists all qualify — the governor normalizes to a set before comparing,
  so the shape of the collection is not the point; being readable as a set of
  named attributes is."
  [x]
  (boolean (and (coll? x)
                (seq x)
                (every? #(not (blank? %)) x))))

(defn usable-confidence?
  "True for a real number in [0,1]. The bounds exclude the infinities, and
  they exclude NaN too — every comparison against NaN is false, so a NaN
  confidence fails `(<= 0 x)` already. No host interop, so `:clj` and `:cljs`
  agree, which is the point: the pre-change comparison did not."
  [x]
  (boolean (and (number? x)
                (>= x 0)
                (<= x 1))))

(defn well-formed-fd?
  "True when both sides of a functional dependency are readable as attribute
  sets. Checked BEFORE the governor coerces them with `set`, because that
  coercion is what threw on the pre-change tree."
  [fd]
  (boolean (and (map? fd)
                (attr-set? (:determinant fd))
                (attr-set? (:dependent fd)))))

(defn vocabulary-violations
  "The op must be declared, and must be one this actor may propose. A reserved
  op is reported separately from an undeclared one: the first is an authority
  boundary, the second is a vocabulary error, and a reviewer needs to be able
  to tell them apart."
  [proposal]
  (let [o (:op proposal)]
    (cond
      (op/supported? o) []

      (op/reserved? o)
      [{:rule :reserved-operation
        :detail (str o " はこの actor の権限外: " (op/reserved-reason o))}]

      :else
      [{:rule :undeclared-operation
        :detail (str (pr-str o) " は dbnet.operation/supported に無い"
                     "（宣言されていない語彙は governor が判定できない）")}])))

(defn provenance-violations
  "The store's record must identify the client the request names. Asking only
  whether the store returned something admitted the record registered under
  the key `nil`."
  [request client-record]
  (cond
    (nil? client-record)
    [{:rule :no-client :detail "未登録 client"}]

    (blank? (:client-id client-record))
    [{:rule :unidentified-client
      :detail "client record に :client-id が無い（store が何かを返したことと、それが client を同定することは別）"}]

    (not= (:client-id client-record) (:client-id request))
    [{:rule :client-mismatch
      :detail (str "request の client-id " (pr-str (:client-id request))
                   " と store の record " (pr-str (:client-id client-record)) " が一致しない")}]

    :else []))

(defn schema-binding-violations
  "An operation that binds to a schema must cite one, and must carry a
  readable, non-empty FD set. Non-binding operations are exempt by
  declaration, not by omission."
  [proposal]
  (let [{:keys [op schema-id fds]} proposal]
    (if-not (op/schema-op? op)
      []
      (cond-> []
        (blank? schema-id)
        (conj {:rule :no-schema-cited
               :detail "schema に束縛する操作が :schema-id を持たない"})

        ;; Absent and empty are the same claim — no functional dependency was
        ;; declared — so they get the same rule. A PRESENT but non-collection
        ;; :fds is a different defect: something was offered as evidence and
        ;; cannot be read. Collapsing the two would let a reviewer read
        ;; "unreadable" when nothing was submitted at all.
        (or (nil? fds) (and (coll? fds) (empty? fds)))
        (conj {:rule :no-fds-declared
               :detail (str "migration が関数従属を 1 つも宣言していない。3NF 検査は "
                            "keep なので空の :fds は無違反として通る —— 正規化を"
                            "検討した証拠が提出されていない")})

        (and (some? fds) (not (coll? fds)))
        (conj {:rule :unreadable-fds
               :detail (str ":fds が集合として読めない: " (pr-str fds))})

        (and (coll? fds) (seq fds) (not (every? well-formed-fd? fds)))
        (conj {:rule :malformed-fd
               :detail (str "determinant/dependent が属性集合として読めない FD が在る: "
                            (pr-str (vec (remove well-formed-fd? fds))))})))))

(defn key-record-violations
  "A registered candidate key must itself be comparable. A key with no
  attributes makes `clojure.set/subset?` true for every determinant, which
  turns the 3NF check off for the whole schema."
  [op keys]
  (if-not (op/schema-op? op)
    []
    (let [bad (remove #(attr-set? (:attrs %)) keys)]
      (if (seq bad)
        [{:rule :unusable-registered-key
          :detail (str "登録済み候補キーに属性集合が無い: "
                       (pr-str (mapv :key-id bad))
                       "（subset? の空前提は全ての determinant を superkey にするので、"
                       "この 1 件が schema 全体の 3NF 検査を無効化する）")}]
        []))))

(defn confidence-violations
  "A present `:confidence` must be a usable number in [0,1]. Absent is left to
  the governor, where it already reads as 0.0 and escalates."
  [proposal]
  (let [c (:confidence proposal)]
    (if (or (nil? c) (usable-confidence? c))
      []
      [{:rule :unusable-confidence
        :detail (str ":confidence " (pr-str c) " は [0,1] の数ではない"
                     "（escalation 判定に使う値なので、意味の無い数で escalation を"
                     "買えてしまう。host によっては例外にも false にもなる）")}])))
