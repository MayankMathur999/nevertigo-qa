
import csv
from pathlib import Path

import joblib
from sentence_transformers import SentenceTransformer

BASE_DIR = Path(__file__).resolve().parent

# 1. Load both classifiers (read-only)
original = joblib.load(BASE_DIR / "experiment_A_classifier.pkl")
v2 = joblib.load(BASE_DIR / "experiment_A_classifier_v2.pkl")

# 2. Load evaluation questions
with open(
    BASE_DIR / "final_test_questions.csv",
    newline="",
    encoding="utf-8-sig"
) as file:
    rows = list(csv.DictReader(file))

# Exclude multi-question examples from single-metric evaluation
rows = [
    row for row in rows
    if not (
        "how many new patients came in may" in row["question"].lower()
        and "cghs" in row["question"].lower()
    )
]

questions = [row["question"] for row in rows]
expected = [row["metric"] for row in rows]

if not questions:
    raise ValueError("No evaluation questions found.")

# 3. Generate embeddings
print("Loading embedding model...")
encoder = SentenceTransformer("all-MiniLM-L6-v2")
vectors = encoder.encode(questions)

print("\nTotal evaluated questions:", len(questions))

# 4. Compare models
for name, model in [("Original", original), ("V2", v2)]:

    predictions = model.predict(vectors)
    probabilities = model.predict_proba(vectors)

    correct = 0
    direct = 0
    confirm = 0
    fallback = 0

    incorrect_direct = 0
    incorrect_confirm = 0
    incorrect_fallback = 0

    incorrect_results = []

    for i, (question, actual, predicted) in enumerate(
        zip(questions, expected, predictions)
    ):
        confidence = float(max(probabilities[i]))
        is_correct = predicted == actual

        if is_correct:
            correct += 1

        # Apply the same confidence thresholds as classifier_service.py
        if confidence >= 0.75:
            route = "DIRECT"
            direct += 1

        elif confidence >= 0.50:
            route = "CONFIRM"
            confirm += 1

        else:
            route = "FALLBACK"
            fallback += 1

        # Record incorrect predictions
        if not is_correct:
            incorrect_results.append({
                "question": question,
                "expected": actual,
                "predicted": predicted,
                "confidence": confidence,
                "route": route
            })

            if route == "DIRECT":
                incorrect_direct += 1
            elif route == "CONFIRM":
                incorrect_confirm += 1
            else:
                incorrect_fallback += 1

    total = len(questions)
    accuracy = correct / total

    # 5. Print summary
    print("\n" + "=" * 60)
    print(f"{name.upper()} MODEL RESULTS")
    print("=" * 60)

    print(f"Accuracy: {correct}/{total} = {accuracy:.2%}")
    print(f"Direct answers: {direct}")
    print(f"Confirmations: {confirm}")
    print(f"Estimated Ollama fallbacks: {fallback}")

    print("\nIncorrect prediction summary:")
    print(f"Total incorrect: {len(incorrect_results)}")
    print(f"Incorrect DIRECT: {incorrect_direct}")
    print(f"Incorrect CONFIRM: {incorrect_confirm}")
    print(f"Incorrect FALLBACK: {incorrect_fallback}")

    # 6. Show incorrect predictions with confidence
    print("\nDetailed incorrect predictions:")

    if not incorrect_results:
        print("No incorrect predictions found.")

    for result in incorrect_results:
        print("-" * 60)
        print(f"Question: {result['question']}")
        print(f"Expected: {result['expected']}")
        print(f"Predicted: {result['predicted']}")
        print(f"Confidence: {result['confidence']:.3f}")
        print(f"Route: {result['route']}")

        if result["route"] == "DIRECT":
            print("WARNING: Incorrect high-confidence prediction!")

    print("\n" + "=" * 60)

print("\nModel comparison completed.")
print("No classifier models were modified.")
