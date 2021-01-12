JPOS Logic Design
=================

## State machine of UI

### Create Connection, keep it and sending

1. Initial state: connectBtn+Address+SingleRound enable, progress bar reseted and off, sendBtn disabled.
2. Click Connect => connectBtn+Address+SingleRound disabled, progress bar set to 30% => ConnectTask start => connectBtn label to "connecting"
3. ConnectTask onSuccess => ready to send, progress bar set to 100% => off in 200ms.
4. ConnectTask onFailed => connectBtn+Address+SingleRound enable, progress bar reseted and off.
5. MTI Set, DE List loaded => sendBtn enable.
5. Click sendBtn => running on, sendBtn disabled, if not connected (defered and run step 2) else progress bar set to 50% => MainService Task start;
6. MainServiceTask onSuccess => running off, progress bar set to 100% => off in 200ms, Message list updated.
7. MainServiceTask onFailed or disconnect => running off, sendBtn enable, connectBtn+Address+SingleRound enable, progress bar reseted and off.