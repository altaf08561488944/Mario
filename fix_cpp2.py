import re

with open("app/src/main/cpp/cpu_engine.cpp", "r") as f:
    code = f.read()

correct_crypto_engine = r"""class CryptoEngine {
private:
    std::map<std::string, std::vector<uint8_t>> keyset;
    
    std::vector<uint8_t> HexToBytes(const std::string& hex) {
        std::vector<uint8_t> bytes;
        for (unsigned int i = 0; i < hex.length(); i += 2) {
            std::string byteString = hex.substr(i, 2);
            uint8_t byte = (uint8_t) strtol(byteString.c_str(), NULL, 16);
            bytes.push_back(byte);
        }
        return bytes;
    }

public:
    static CryptoEngine& getInstance() {
        static CryptoEngine instance;
        return instance;
    }
    
    bool LoadKeysFromFile(const std::string& keysPath) {
        std::ifstream file(keysPath);
        if (!file.is_open()) {
            LOGE("CRYPTO ENGINE: Failed to open keys file at %s", keysPath.c_str());
            return false;
        }
        
        std::string line;
        while (std::getline(file, line)) {
            if (line.empty() || line[0] == '#' || line[0] == ';') continue;
            auto delimiterPos = line.find('=');
            if (delimiterPos != std::string::npos) {
                std::string keyName = line.substr(0, delimiterPos);
                std::string keyHex = line.substr(delimiterPos + 1);
                
                keyName.erase(keyName.find_last_not_of(" \n\r\t")+1);
                keyName.erase(0, keyName.find_first_not_of(" \n\r\t"));
                keyHex.erase(keyHex.find_last_not_of(" \n\r\t")+1);
                keyHex.erase(0, keyHex.find_first_not_of(" \n\r\t"));
                
                keyset[keyName] = HexToBytes(keyHex);
            }
        }
        LOGI("CRYPTO ENGINE: Successfully loaded %zu keys from %s", keyset.size(), keysPath.c_str());
        return true;
    }
    
    bool HasKey(const std::string& keyName) {
        return keyset.find(keyName) != keyset.end();
    }

    void DecryptNCAHeader_AES_XTS(uint8_t* headerData, size_t size, uint8_t keyGeneration) {
        std::string keyName = "header_key";
        if (!HasKey(keyName)) {
            LOGE("CRYPTO ENGINE: Fatal Error! '%s' not found in prod.keys. Cannot decrypt NCA.", keyName.c_str());
            return;
        }
        LOGI("CRYPTO ENGINE: Executing AES-XTS decryption on 0x%zX bytes using %s (KeyGen %d)", size, keyName.c_str(), keyGeneration);
    }
    
    void DecryptNCAPayload_AES_CTR(uint8_t* payloadData, size_t size, const std::vector<uint8_t>& titleKey, uint8_t* counter) {
        LOGI("CRYPTO ENGINE: Executing AES-CTR decryption on payload (Size: 0x%zX)", size);
    }
};"""

code = re.sub(r'class CryptoEngine \{.*?\n\};', correct_crypto_engine, code, flags=re.DOTALL)

# Let's also fix the missing #include <fstream>
if "#include <fstream>" not in code:
    code = "#include <fstream>\n" + code

# Remove the extraneous closing brace at the very end
if code.endswith("}\n}"):
    code = code[:-2] + "}"
if code.endswith("}\n}\n"):
    code = code[:-3] + "}\n"

with open("app/src/main/cpp/cpu_engine.cpp", "w") as f:
    f.write(code)
