import re

with open("engine/src/main/cpp/host/mb_abi.h", "r") as f:
    content = f.read()
target = """  int32_t compare;  /* -1 when unused */
} mb_cheat;"""
replacement = """  int32_t compare;  /* -1 when unused */
  int32_t id;       /* unique id assigned by host */
} mb_cheat;"""
content = content.replace(target, replacement)
with open("engine/src/main/cpp/host/mb_abi.h", "w") as f:
    f.write(content)

with open("engine/src/main/cpp/host/host.cpp", "r") as f:
    content = f.read()
target = """  out->compare = e.decoded.compare;
  return MB_OK;"""
replacement = """  out->compare = e.decoded.compare;
  out->id = e.id;
  return MB_OK;"""
content = content.replace(target, replacement)
with open("engine/src/main/cpp/host/host.cpp", "w") as f:
    f.write(content)

with open("engine/src/main/cpp/platform/android/mariobox_jni.cpp", "r") as f:
    content = f.read()
target = """  kCheatCompare,
  kCheatCount
};"""
replacement = """  kCheatCompare,
  kCheatId,
  kCheatCount
};"""
content = content.replace(target, replacement)
target2 = """  snprintf(num, sizeof(num), "%d", c.compare);
  (*out)[kCheatCompare] = num;
}"""
replacement2 = """  snprintf(num, sizeof(num), "%d", c.compare);
  (*out)[kCheatCompare] = num;
  snprintf(num, sizeof(num), "%d", c.id);
  (*out)[kCheatId] = num;
}"""
content = content.replace(target2, replacement2)
with open("engine/src/main/cpp/platform/android/mariobox_jni.cpp", "w") as f:
    f.write(content)
