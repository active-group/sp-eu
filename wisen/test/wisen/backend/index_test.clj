(ns wisen.backend.index-test
  (:require [clojure.test :refer [deftest testing is]]
            [wisen.backend.index :as i]))

(deftest senioren-test
  (let [dir (i/make-in-memory-index)
        insert! (fn [id title description]
                  (i/insert! id 1.0 1.0
                             title
                             description
                             dir))
        search! (fn [text]
                  (i/search! text
                             (i/make-bounding-box
                              0.0 2.0
                              0.0 2.0)
                             dir))]

    (insert! "senioren"

             "Seniorentreff freshe Omas"

             "Bei diesem Seniorentreff kommen Omas ins Gespräch über dies und das.")

    (insert! "queerfeministisch"

             "TINT Filmkollektiv"

             "TINT ist ein queerfeministisches
               Filmemacher*innenkollektiv in Berlin. TINT setzt
               Filmprojekte von der Konzeption bis zur Postproduktion
               zu gesellschaftlich relevanten Themen um und engagiert
               sich für Geschlechtergerechtigkeit im Film.")

    (insert! "fussball"

             "Fußball für Erwachsene – Campus Asyl"

             "Ob im Winter oder im Sommer – bei Campus Asyl gibt es
               regelmäßig die Gelegenheit, Fußball zu spielen. Jeder,
               der Lust auf Bewegung hat, ist herzlich eingeladen,
               mitzuspielen, unabhängig von der Herkunft. Dank des
               interkulturellen Formats finden sich auch immer
               Mitspielende mit verschiedenen Fremdsprachkenntnissen,
               wie z. B. arabisch oder englisch. Deutschkenntnisse
               sind nicht erforderlich. In unserer Erfahrung ist der
               Großteil der Mitspielenden zwischen 16 und etwa 30
               Jahre alt. Falls du Interesse hast, das Fußballspielen
               mit zu betreuen und zu organisieren: Campus Asyl sucht
               immer nach Freiwilligen, die unterstützen wollen und
               mit Freude dabei sind.")

    (insert! "gleichstellungsbeauftragte"

             "Gleichstellungsbeauftragte"

             "Die Gleichstellungsbeauftragte setzt sich für die
             Gleichstellung von Frauen und Männern im Bezirksamt
             Neukölln ein. Sie berät und unterstützt Frauen in allen
             Fragen der Gleichstellung, insbesondere bei
             Diskriminierung und Benachteiligung. Sie wirkt an allen
             Entscheidungen des Bezirksamtes mit, die Auswirkungen auf
             die Gleichstellung haben können. Sie ist
             Ansprechpartnerin für alle Beschäftigten des Bezirksamtes
             und für die Bürgerinnen und Bürger Neuköllns.")

    (is (= "senioren"
           (first
            (search! "Senioren"))))

    (is (= "queerfeministisch"
           (first
            (search! "queer"))))

    (is (= "fussball"
           (first
            (search! "migranten"))))

    (is (= "gleichstellungsbeauftragte"
           (first
            (search! "gleichstellung"))))

    (is (= "senioren"
           (first
            (search! "Alte Menschen"))))))
