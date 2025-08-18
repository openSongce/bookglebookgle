from src.services.discussion_service import DiscussionService
import inspect

ds = DiscussionService()
sig = inspect.signature(ds.process_chat_message)
print('Signature:', sig)
print('Parameters:', list(sig.parameters.keys()))

try:
    result = ds.process_chat_message('test_session', {'message': 'test'})
    print('SUCCESS: Method call worked')
except Exception as e:
    print('ERROR:', e)