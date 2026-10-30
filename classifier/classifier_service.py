
from flask import Flask, request, jsonify
from sentence_transformers import SentenceTransformer
import joblib
import logging
import time
from pathlib import Path

app = Flask(__name__)

# --------------------------------------------------
# 1. Logging configuration
# --------------------------------------------------

LOG_FILE = Path("classifier_questions.log")

logging.basicConfig(
    filename=str(LOG_FILE),
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(message)s",
    encoding="utf-8",
    force=True
)

logger = logging.getLogger(__name__)

# --------------------------------------------------
# 2. Load Experiment A V2 classifier and embedding model
# --------------------------------------------------

MODEL_PATH = Path("classifier/experiment_A_classifier_v2_best.pkl")

try:
    classifier = joblib.load(MODEL_PATH)

    print("LOADED MODEL:", MODEL_PATH.resolve(), flush=True)
    print(
        "MODEL COEFFICIENT SUM:",
        round(float(abs(classifier.coef_).sum()), 4),
        flush=True
    )

    embedding_model = SentenceTransformer("all-MiniLM-L6-v2")

    logger.info(
        "Experiment A classifier and embedding model loaded successfully."
    )

except Exception:
    logger.exception("Failed to load classifier or embedding model.")
    raise

# --------------------------------------------------
# 3. Confidence thresholds
# --------------------------------------------------

HIGH_CONFIDENCE = 0.75
MEDIUM_CONFIDENCE = 0.50

# --------------------------------------------------
# 4. Prediction API
# --------------------------------------------------

@app.route("/predict", methods=["POST"])
def predict():
    request_started = time.perf_counter()

    data = request.get_json(silent=True) or {}
    question = data.get("question", "")

    if not isinstance(question, str) or not question.strip():
        return jsonify({
            "error": "Question is required"
        }), 400

    question = question.strip()
    prediction_started = time.perf_counter()

    try:
        # Convert question into an embedding
        vector = embedding_model.encode([question])

        # Diagnostic information
        print(
            "PREDICTION MODEL:",
            MODEL_PATH.name,
            flush=True
        )

        print(
            "PREDICTION COEFFICIENT SUM:",
            round(float(abs(classifier.coef_).sum()), 4),
            flush=True
        )

        # Predict the most likely metric
        probabilities = classifier.predict_proba(vector)[0]
        best_index = probabilities.argmax()

        metric = str(classifier.classes_[best_index])
        confidence = float(probabilities[best_index])

        # Select outcome using confidence thresholds
        if confidence >= HIGH_CONFIDENCE:
            outcome = "ANSWER"
            message = "High confidence"

        elif confidence >= MEDIUM_CONFIDENCE:
            outcome = "CONFIRM"
            message = "Did you mean this metric?"

        else:
            outcome = "NOT_SUPPORTED"
            metric = "NONE"
            message = "Not supported yet"

        # Measure classifier processing time
        prediction_time = time.perf_counter() - prediction_started

        # Measure total API request time
        total_time = time.perf_counter() - request_started

        # Log question and performance
        logger.info(
            "question=%r metric=%s confidence=%.4f outcome=%s "
            "prediction_time_seconds=%.4f total_time_seconds=%.4f",
            question,
            metric,
            confidence,
            outcome,
            prediction_time,
            total_time
        )

        # Response compatible with Spring Boot
        return jsonify({
            "metric": metric,
            "confidence": round(confidence, 4),
            "outcome": outcome,
            "message": message
        })

    except Exception:
        logger.exception(
            "Prediction failed for question=%r",
            question
        )

        return jsonify({
            "error": "Classifier prediction failed"
        }), 500


# --------------------------------------------------
# 5. Start Flask server
# --------------------------------------------------

if __name__ == "__main__":
    app.run(host="127.0.0.1", port=5000)
