private String generateTOTP(String secretKey, long timeStep) throws Exception {
        byte[] key = Base64.getDecoder().decode(secretKey);
        long time = Instant.now().getEpochSecond() / timeStep;
        Mac mac = Mac.getInstance("HmacSHA1");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "HmacSHA1");
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(longToBytes(time));
        int offset = hash[hash.length - 1] & 0x0f;
        int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16) | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
        return String.format("%06d", binary % 1000000);
    }

    private byte[] longToBytes(long x) {
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte)(x & 0xff);
            x >>= 8;
        }
        return result;