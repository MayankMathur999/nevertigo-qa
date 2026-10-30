from pathlib import Path
import joblib
from sentence_transformers import SentenceTransformer
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, accuracy_score

import csv

# 1. File locations
BASE_DIR = Path(__file__).resolve().parent
DATA_PATH = BASE_DIR / "training_data_v2.csv"
MODEL_PATH = BASE_DIR / "experiment_A_classifier_v2.pkl"

# 2. Load training examples
with open(DATA_PATH, "r", encoding="utf-8-sig", newline="") as file:
    rows = list(csv.DictReader(file))

questions = [row["question"].strip() for row in rows]
labels = [row["metric"].strip() for row in rows]

print("Total questions:", len(questions))
print("Total classes:", len(set(labels)))

# 3. Split data before training
X_train, X_test, y_train, y_test = train_test_split(
    questions,
    labels,
    test_size=0.25,
    random_state=42,
    stratify=labels
)

# 4. Generate embeddings
print("Loading embedding model...")
embedding_model = SentenceTransformer("all-MiniLM-L6-v2")

train_vectors = embedding_model.encode(X_train)
test_vectors = embedding_model.encode(X_test)

# 5. Train Experiment A V2
print("Training Experiment A V2...")

classifier = LogisticRegression(
    max_iter=2000,
    C=10.0
)

classifier.fit(train_vectors, y_train)

# 6. Evaluate on held-out questions
predictions = classifier.predict(test_vectors)
original_model = joblib.load(BASE_DIR / "experiment_A_classifier.pkl")
original_predictions = original_model.predict(test_vectors)

print("\nOriginal model validation accuracy:",
      accuracy_score(y_test, original_predictions))

print("V2 model validation accuracy:",
      accuracy_score(y_test, predictions))

print("\nValidation accuracy:", accuracy_score(y_test, predictions))
print("\nClassification report:")
print(classification_report(
    y_test,
    predictions,
    zero_division=0
))

# 7. Save V2 separately
joblib.dump(classifier, MODEL_PATH)

print("\nV2 model saved to:", MODEL_PATH)
print("Original Experiment A model was not modified.")
