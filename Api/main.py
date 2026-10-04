from fastapi import FastAPI
from google import genai
from databricks.sdk import WorkspaceClient
from dotenv import load_dotenv
import os
from pathlib import Path

if __package__:
    from .contracts import DashboardRequest, DashboardResponse
    from .dashboard_analysis import DatabricksDashboardAnalyzer
else:  # Preserve uvicorn main:app launched from the Api directory.
    from contracts import DashboardRequest, DashboardResponse
    from dashboard_analysis import DatabricksDashboardAnalyzer

load_dotenv(Path(__file__).with_name('.env'))
app = FastAPI()
app.state.dashboard_analyzer = DatabricksDashboardAnalyzer()

@app.post("/make_prediction", response_model=DashboardResponse)
def make_dashboard_prediction(request: DashboardRequest):
    # FastAPI runs synchronous endpoints in its worker pool, keeping the event loop free.
    return app.state.dashboard_analyzer.analyze(request)


@app.get("/make_prediction")
def make_prediction(heartbeat: str, glucose: str, Interbeat_interval: str, ACC: str)->str:

    load_dotenv()
    space_id = os.getenv("space_id")
    

    w = WorkspaceClient()
    if space_id is None:
        return "unable to load environment variable"

    chat = w.genie.start_conversation_and_wait(space_id=space_id,
                                               content="Based on the data you have been provided in your training data, determine which patients have prediabetes and which do not, and return your findings formatted as a table, with the patient number being column one and column two being if they have prediabetes or not, present the information in a way that I can give it to an LLM via api to teach it patterns")

    client = genai.Client()
   
    input_text = f"""
                Using the patterns and relationships found in the provided dataset, analyze the new person's health data and predict whether they are likely to have diabetes.

                Use only the information and patterns supported by the dataset. Do not invent missing values or unsupported relationships.

                Return:
                confidence_score: a value from 0.01 to 1.00

                A confidence score closer to 1.00 should indicate that the person's data strongly matches patterns associated with the predicted class. Use a lower confidence score when the data is ambiguous, incomplete, or does not strongly match either class.
                {chat.as_dict()}
                """

    interaction = client.chats.create(model="gemini-3.8-flash")
    interaction.send_message(input_text)

    patient_info = f"""patients info that you have to make a prediction using: {heartbeat}, {glucose}, {Interbeat_interval}, {ACC}"""

    response = interaction.send_message(patient_info)

    if response.text is None:
        return ""
    else:
        return response.text
    

