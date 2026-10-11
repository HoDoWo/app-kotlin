# Howdowo
How does it work? A utility to guide when using home appliances

## Running the Android app

The app calls the deployed FastAPI backend. Add the backend base URL to
`App-HoDoWo/local.properties`:

```properties
backend.url=https://backend-how-do-wo.vercel.app
```

Do not add `/api/chat` to this value; the app appends that path when sending
a message. The `local.properties` file is ignored by Git and should not be
committed.

# Just notes
1. Favs page
2. STT 
3. Reinforcement learning 
4. forum, also used to fine tune prediction
5. Multiple step agent (langchain)
	- product identification 
	- information retrieval 
	- image generation 
6. "Broken" appliance detection 
	1. Parts replacement 
	2. New device recommendations (based on enterprise partners)
7. Off-peak electricity tariff optimization
8. Acoustic thing
# Tecnologies
1. Android Studio (Using Kotlin)
2. LangChain for the AI agent, 3 step
3. AI Image Identification
4. Digital Twin of the user's home
# Additional Features
1- Recommendation products secction (For old or broken user machines) (Companies can partner us to recommend their products on this place)
2- Second Hand Market secction inside the app
