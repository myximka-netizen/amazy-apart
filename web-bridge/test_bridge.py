import unittest
import bridge

class TestBridge(unittest.TestCase):
    def test_url(self):
        self.assertTrue(bridge.safe_url("https://extranet.sutochno.ru/messages"))
        for url in ["http://extranet.sutochno.ru","https://extranet.sutochno.ru.evil.test","https://foo@extranet.sutochno.ru","https://example.com"]:
            self.assertFalse(bridge.safe_url(url))
    def test_outgoing(self):
        p={"event":"message_created","message_type":"outgoing","private":False,"account":{"id":bridge.ACCOUNT},"inbox":{"id":bridge.INBOX},"conversation":{"id":1},"id":2}
        self.assertTrue(bridge.outgoing_event(p))
        for k,v in [("private",True),("message_type","incoming"),("account",{"id":999}),("inbox",{"id":999})]:
            self.assertFalse(bridge.outgoing_event({**p,k:v}))
    def test_inspect(self):
        bridge.validate({"adapter":"sutochno","mode":"inspect","start_url":"https://extranet.sutochno.ru/"})
    def test_read_without_configuration(self):
        with self.assertRaises(ValueError):
            bridge.validate({"adapter":"sutochno","mode":"read","start_url":"https://extranet.sutochno.ru/"})

if __name__=="__main__":
    unittest.main()
